package com.oikos.fieldbook.services

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.oikos.fieldbook.util.UiLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Envio do pacote de projeto direto para um celular proximo, sem internet e
 * sem parear: o Nearby Connections do Google Play services escolhe sozinho
 * entre Bluetooth, BLE e Wi-Fi direto. Quem recebe anuncia o aparelho; quem
 * envia procura, escolhe e os dois conferem o mesmo codigo de 4 digitos antes
 * de quem recebe aceitar.
 *
 * O pacote vai como STREAM (nao passa pela pasta Downloads nem pede permissao
 * de armazenamento), depois de um cabecalho com tamanho e SHA-256; quem recebe
 * confere os dois e responde se chegou inteiro.
 */
class NearbyTransfer(context: Context) {

    sealed interface State {
        data object Idle : State
        /** Quem envia: procurando celulares com "Receber" aberto. */
        data class Searching(val peers: List<Peer>) : State
        /** Quem recebe: visivel para os celulares proximos com este nome. */
        data class Waiting(val myName: String) : State
        /** Quem recebe: pedido de conexao esperando o "Aceitar". */
        data class Incoming(val endpointId: String, val name: String, val code: String) : State
        /** Quem envia: esperando o outro aceitar, com o codigo para conferir. */
        data class Connecting(val name: String, val code: String) : State
        data class Transferring(val name: String, val bytes: Long, val total: Long) : State
        /** Quem envia: pacote todo enviado, esperando a confirmacao do outro. */
        data class Confirming(val name: String) : State
        data class Sent(val name: String) : State
        data class Received(val file: File, val name: String) : State
        data class Failed(val message: String) : State
    }

    data class Peer(val endpointId: String, val name: String)

    private enum class Role { NONE, SENDER, RECEIVER }

    private val appContext = context.applicationContext
    private val client = Nearby.getConnectionsClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var role = Role.NONE
    private var myName = ""
    private var peerName = ""
    private var endpoint: String? = null
    private val peers = linkedMapOf<String, Peer>()

    // Quem envia
    private var outgoing: File? = null
    private var outgoingHeader: JSONObject? = null
    private var streamPayloadId: Long? = null

    // Quem recebe
    private var incomingHeader: JSONObject? = null
    private var incomingFile: File? = null
    private var finished = false

    /** Nome com que este celular aparece para os outros (nome do aparelho nas configuracoes). */
    fun deviceName(): String =
        runCatching { Settings.Global.getString(appContext.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: listOf(Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")

    /** Abre o "Receber": este celular fica visivel para quem for enviar. */
    fun startReceiving() {
        reset()
        role = Role.RECEIVER
        myName = deviceName()
        _state.value = State.Waiting(myName)
        client.startAdvertising(myName, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail(it) }
    }

    /** Quem recebe aceita o pedido depois de conferir o codigo. */
    fun accept(endpointId: String) {
        client.acceptConnection(endpointId, payloads).addOnFailureListener { fail(it) }
        _state.value = State.Connecting(peerName, (_state.value as? State.Incoming)?.code.orEmpty())
    }

    fun reject(endpointId: String) {
        client.rejectConnection(endpointId)
        _state.value = State.Waiting(myName)
    }

    /**
     * Abre o "Enviar": procura celulares com o "Receber" aberto. [file] e o
     * pacote ja montado; [senderName] e o nome que o outro vai ver.
     */
    fun startSending(file: File, senderName: String, projectName: String) {
        reset()
        role = Role.SENDER
        myName = senderName.ifBlank { deviceName() }
        outgoing = file
        scope.launch {
            outgoingHeader = JSONObject()
                .put("format", HEADER_FORMAT)
                .put("fileName", file.name)
                .put("size", file.length())
                .put("sha256", sha256(file))
                .put("projectName", projectName)
                .put("senderName", senderName)
            _state.value = State.Searching(emptyList())
            client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
                .addOnFailureListener { fail(it) }
        }
    }

    fun connectTo(peer: Peer) {
        peerName = peer.name
        _state.value = State.Connecting(peer.name, "")
        client.requestConnection(myName, peer.endpointId, lifecycle).addOnFailureListener { fail(it) }
    }

    /** Fecha tudo: para de anunciar/procurar e desconecta. O arquivo recebido fica para a importacao. */
    fun stop() {
        reset()
        _state.value = State.Idle
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private fun reset() {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        if (role == Role.RECEIVER && !finished) incomingFile?.delete()
        role = Role.NONE
        endpoint = null
        peerName = ""
        peers.clear()
        outgoing = null
        outgoingHeader = null
        streamPayloadId = null
        incomingHeader = null
        incomingFile = null
        finished = false
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID || role != Role.SENDER) return
            peers[endpointId] = Peer(endpointId, info.endpointName)
            if (_state.value is State.Searching) _state.value = State.Searching(peers.values.toList())
        }

        override fun onEndpointLost(endpointId: String) {
            peers.remove(endpointId)
            if (_state.value is State.Searching) _state.value = State.Searching(peers.values.toList())
        }
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            peerName = info.endpointName
            val code = info.authenticationDigits
            if (role == Role.SENDER) {
                // Quem envia pediu a conexao: aceita do seu lado e espera o outro.
                client.acceptConnection(endpointId, payloads).addOnFailureListener { fail(it) }
                _state.value = State.Connecting(info.endpointName, code)
            } else {
                _state.value = State.Incoming(endpointId, info.endpointName, code)
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            when (resolution.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    endpoint = endpointId
                    if (role == Role.SENDER) {
                        runCatching { client.stopDiscovery() }
                        send(endpointId)
                    } else {
                        runCatching { client.stopAdvertising() }
                        _state.value = State.Transferring(peerName, 0, 0)
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                    if (role == Role.SENDER) fail(UiLanguage.t("o outro celular recusou o envio", "the other phone declined the transfer", "el otro celular rechazó el envío"))
                    else _state.value = State.Waiting(myName)
                else -> fail(UiLanguage.t("não foi possível conectar (${resolution.status.statusCode})", "could not connect (${resolution.status.statusCode})", "no se pudo conectar (${resolution.status.statusCode})"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (finished) return
            when (val current = _state.value) {
                is State.Sent, is State.Received, is State.Failed, is State.Idle -> Unit
                is State.Confirming -> fail(UiLanguage.t("a conexão caiu antes da confirmação; confira no outro celular se o pacote chegou", "the connection dropped before the confirmation; check on the other phone whether the package arrived", "la conexión se cayó antes de la confirmación; revisa en el otro celular si el paquete llegó"))
                else -> if (current !is State.Waiting && current !is State.Searching) {
                    fail(UiLanguage.t("a conexão caiu. Aproximem os celulares e tentem de novo", "the connection dropped. Bring the phones closer and try again", "la conexión se cayó. Acerquen los celulares e inténtenlo de nuevo"))
                }
            }
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val json = runCatching { JSONObject(String(payload.asBytes() ?: return, Charsets.UTF_8)) }.getOrNull() ?: return
                    if (role == Role.RECEIVER && json.optString("format") == HEADER_FORMAT) {
                        incomingHeader = json
                        val size = json.optLong("size")
                        if (appContext.cacheDir.usableSpace < size + FREE_SPACE_MARGIN) {
                            reply(endpointId, false, UiLanguage.t("o celular que recebe está sem espaço", "the receiving phone is out of space", "el celular que recibe no tiene espacio"))
                            fail(UiLanguage.t("sem espaço para receber o pacote (${size / (1024 * 1024)} MB)", "not enough space to receive the package (${size / (1024 * 1024)} MB)", "no hay espacio para recibir el paquete (${size / (1024 * 1024)} MB)"))
                        } else {
                            _state.value = State.Transferring(peerName, 0, size)
                        }
                    } else if (role == Role.SENDER && json.has("ok")) {
                        finished = true
                        if (json.optBoolean("ok")) _state.value = State.Sent(peerName)
                        else fail(json.optString("error").ifBlank { UiLanguage.t("o outro celular não conseguiu receber o pacote", "the other phone could not receive the package", "el otro celular no pudo recibir el paquete") })
                        runCatching { client.disconnectFromEndpoint(endpointId) }
                    }
                }
                Payload.Type.STREAM -> if (role == Role.RECEIVER) receiveStream(endpointId, payload)
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    val total = if (role == Role.SENDER) outgoing?.length() ?: update.totalBytes else incomingHeader?.optLong("size") ?: update.totalBytes
                    if (role == Role.SENDER && update.payloadId != streamPayloadId) return
                    if (_state.value is State.Transferring || _state.value is State.Connecting) {
                        _state.value = State.Transferring(peerName, update.bytesTransferred, total)
                    }
                }
                PayloadTransferUpdate.Status.SUCCESS ->
                    if (role == Role.SENDER && update.payloadId == streamPayloadId) _state.value = State.Confirming(peerName)
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED ->
                    if (!finished) fail(UiLanguage.t("o envio foi interrompido. Aproximem os celulares e tentem de novo", "the transfer was interrupted. Bring the phones closer and try again", "el envío se interrumpió. Acerquen los celulares e inténtenlo de nuevo"))
            }
        }
    }

    private fun send(endpointId: String) {
        val file = outgoing ?: return fail(UiLanguage.t("pacote não encontrado", "package not found", "paquete no encontrado"))
        val header = outgoingHeader ?: return fail(UiLanguage.t("pacote não encontrado", "package not found", "paquete no encontrado"))
        client.sendPayload(endpointId, Payload.fromBytes(header.toString().toByteArray(Charsets.UTF_8))).addOnFailureListener { fail(it) }
        val stream = Payload.fromStream(FileInputStream(file))
        streamPayloadId = stream.id
        _state.value = State.Transferring(peerName, 0, file.length())
        client.sendPayload(endpointId, stream).addOnFailureListener { fail(it) }
    }

    /** Copia o pacote para o cache conferindo tamanho e SHA-256, e responde ao outro celular. */
    private fun receiveStream(endpointId: String, payload: Payload) {
        val input = payload.asStream()?.asInputStream() ?: return
        scope.launch {
            val folder = File(appContext.cacheDir, RECEIVED_DIR).apply { mkdirs() }
            folder.listFiles().orEmpty().filter { it.lastModified() < System.currentTimeMillis() - STALE_MILLIS }.forEach { it.delete() }
            val target = File(folder, "pacote-${System.currentTimeMillis()}.zip")
            incomingFile = target
            val result = runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                input.use { source ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            count += read
                        }
                    }
                }
                val header = incomingHeader
                val expectedSize = header?.optLong("size") ?: -1L
                val expectedSha = header?.optString("sha256").orEmpty()
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                if (header == null || count != expectedSize || sha != expectedSha) {
                    throw java.io.IOException(UiLanguage.t("o pacote chegou incompleto", "the package arrived incomplete", "el paquete llegó incompleto"))
                }
            }
            if (result.isSuccess) {
                finished = true
                reply(endpointId, true, "")
                _state.value = State.Received(target, header?.optString("senderName").orEmpty().ifBlank { peerName })
            } else {
                target.delete()
                reply(endpointId, false, result.exceptionOrNull()?.message.orEmpty())
                fail(result.exceptionOrNull()?.message ?: UiLanguage.t("o pacote chegou incompleto", "the package arrived incomplete", "el paquete llegó incompleto"))
            }
        }
    }

    private val header: JSONObject? get() = incomingHeader

    private fun reply(endpointId: String, ok: Boolean, error: String) {
        val json = JSONObject().put("ok", ok).put("error", error)
        client.sendPayload(endpointId, Payload.fromBytes(json.toString().toByteArray(Charsets.UTF_8)))
    }

    private fun fail(error: Exception) {
        val code = (error as? ApiException)?.statusCode
        fail(
            when (code) {
                ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH, ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
                ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_WIFI_STATE, ConnectionsStatusCodes.MISSING_PERMISSION_CHANGE_WIFI_STATE,
                ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION ->
                    UiLanguage.t("falta permissão de dispositivos próximos ou de localização ($code)", "the nearby devices or location permission is missing ($code)", "falta el permiso de dispositivos cercanos o de ubicación ($code)")
                ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR, ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                    UiLanguage.t("ligue o Bluetooth e o Wi-Fi (não precisa de internet) e tente de novo", "turn on Bluetooth and Wi-Fi (no internet needed) and try again", "activa el Bluetooth y el Wi-Fi (no hace falta internet) e inténtalo de nuevo")
                ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN ->
                    UiLanguage.t("o outro celular saiu de alcance", "the other phone is out of range", "el otro celular quedó fuera de alcance")
                else -> error.message ?: error.toString()
            }
        )
    }

    private fun fail(message: String) {
        if (_state.value is State.Received || _state.value is State.Sent) return
        val keepFile = finished && role == Role.RECEIVER
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        if (!keepFile) incomingFile?.delete()
        _state.value = State.Failed(message)
    }

    companion object {
        private const val SERVICE_ID = "com.oikos.fieldbook.pacote"
        private const val HEADER_FORMAT = "oikos-envio-1"
        private const val RECEIVED_DIR = "recebidos"
        private const val STALE_MILLIS = 24 * 60 * 60 * 1000L
        private const val FREE_SPACE_MARGIN = 50L * 1024 * 1024
        private val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT

        /** Permissoes pedidas na hora de usar, conforme a versao do Android (documentacao do Nearby). */
        fun requiredPermissions(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }.toTypedArray()

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
