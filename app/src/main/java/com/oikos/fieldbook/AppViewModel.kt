package com.oikos.fieldbook

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteConstraintException
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oikos.fieldbook.data.FieldConfigEntity
import com.oikos.fieldbook.data.GpsTrackEntity
import com.oikos.fieldbook.data.GpsTrackPointEntity
import com.oikos.fieldbook.data.MediaEntity
import com.oikos.fieldbook.data.OccurrenceEntity
import com.oikos.fieldbook.data.OikosRepository
import com.oikos.fieldbook.data.ProjectEntity
import com.oikos.fieldbook.data.SessionEntity
import com.oikos.fieldbook.export.ExportContent
import com.oikos.fieldbook.export.ExportFormat
import com.oikos.fieldbook.export.OikosExporter
import com.oikos.fieldbook.export.TrackExportBundle
import com.oikos.fieldbook.export.TrackPointExport
import com.oikos.fieldbook.services.CapturedLocation
import com.oikos.fieldbook.services.LocationService
import com.oikos.fieldbook.services.MediaFileService
import com.oikos.fieldbook.services.PhotoMetadata
import com.oikos.fieldbook.services.PureMp3AudioRecorder
import com.oikos.fieldbook.services.TrackPointRules
import com.oikos.fieldbook.services.TrackRecordingService
import com.oikos.fieldbook.services.WavAudioRecorder
import com.oikos.fieldbook.util.CoordinateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Destino de uma midia recem-capturada: [draftKey] nulo grava direto no banco
 * (midia da expedicao ou de ocorrencia ja salva); preenchido, a midia fica
 * pendente no formulario da ocorrencia nova ate ela ser salva.
 */
data class MediaTarget(
    val projectId: Long,
    val sessionId: Long,
    val occurrenceId: Long?,
    val draftKey: String?
)

/** Gravacao de audio em andamento. Vive no ViewModel para sobreviver a rotacao. */
data class RecordingState(
    val filePath: String,
    val format: String,
    val startedAt: Long,
    val target: MediaTarget,
    val subject: String,
    val description: String
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: OikosRepository = (application as OikosApplication).repository
    private val exporter = OikosExporter()
    private val locationService = LocationService(application)
    val mediaFiles = MediaFileService(application)
    private val settings = application.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)

    val projects: Flow<List<ProjectEntity>> = repository.observeProjects()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting

    private val _recording = MutableStateFlow<RecordingState?>(null)
    val recording: StateFlow<RecordingState?> = _recording
    private val wavRecorder = WavAudioRecorder()
    private val mp3Recorder = PureMp3AudioRecorder()

    /** Midias capturadas para ocorrencias ainda nao salvas, por chave de rascunho. */
    private val _pendingMedia = MutableStateFlow<Map<String, List<MediaEntity>>>(emptyMap())
    val pendingMedia: StateFlow<Map<String, List<MediaEntity>>> = _pendingMedia

    init {
        resumeInterruptedTracks()
    }

    fun observeProject(projectId: Long) = repository.observeProject(projectId)
    fun observeFields(projectId: Long) = repository.observeFields(projectId)
    fun observeSessions(projectId: Long) = repository.observeSessions(projectId)
    fun observeSession(sessionId: Long) = repository.observeSession(sessionId)
    fun observeOccurrences(sessionId: Long) = repository.observeOccurrences(sessionId)
    fun observeOccurrence(occurrenceId: Long) = repository.observeOccurrence(occurrenceId)
    fun observeSessionMedia(sessionId: Long) = repository.observeSessionMedia(sessionId)
    fun observeOccurrenceMedia(occurrenceId: Long) = repository.observeOccurrenceMedia(occurrenceId)
    fun observeTracks(sessionId: Long) = repository.observeTracks(sessionId)
    fun observeTrackPoints(trackId: Long) = repository.observeTrackPoints(trackId)

    // ---------------------------------------------------------------- ajustes

    /** Geocodificacao reversa: envia a coordenada ao servico do Google. Desligada por padrao. */
    var reverseGeocodingEnabled: Boolean
        get() = settings.getBoolean(KEY_REVERSE_GEOCODING, false)
        set(value) {
            settings.edit().putBoolean(KEY_REVERSE_GEOCODING, value).apply()
        }

    // ---------------------------------------------------------------- projetos

    fun saveProject(project: ProjectEntity, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = if (project.id == 0L) repository.createProject(project) else {
                repository.updateProject(project)
                project.id
            }
            onSaved(id)
        }
    }

    fun deleteProject(project: ProjectEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            stopTrackServiceIf { it.projectId == project.id }
            if (_recording.value?.target?.projectId == project.id) cancelRecording()
            val media = repository.getMedia(project.id)
            repository.deleteProject(project)
            withContext(Dispatchers.IO) {
                media.forEach { mediaFiles.deleteIfManaged(it.filePath) }
                mediaFiles.deleteProjectFiles(project.id)
            }
            _message.value = "Projeto apagado."
            onDone()
        }
    }

    fun updateField(field: FieldConfigEntity) {
        viewModelScope.launch { repository.updateField(field) }
    }

    fun saveFieldConfiguration(projectId: Long, fields: List<FieldConfigEntity>) {
        viewModelScope.launch {
            repository.replaceFieldConfig(projectId, fields)
            _message.value = "Configuração de campos salva."
        }
    }

    fun ensureFieldCatalog(projectId: Long) {
        viewModelScope.launch { repository.ensureFieldCatalog(projectId) }
    }

    // ---------------------------------------------------------------- expedicoes

    fun saveSession(session: SessionEntity, onSaved: (Long) -> Unit) {
        val error = validateCoordinates(session.latitude, session.longitude)
        if (error != null) {
            _message.value = error
            return
        }
        if (session.eventDate.isBlank()) _message.value = "Aviso: expedição salva sem data de coleta."
        viewModelScope.launch {
            try {
                val id = if (session.id == 0L) repository.createSession(session) else {
                    repository.updateSession(session)
                    session.id
                }
                onSaved(id)
            } catch (_: SQLiteConstraintException) {
                _message.value = "eventID já existe. Use um identificador único."
            }
        }
    }

    fun deleteSession(session: SessionEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            // So para a trilha se ela for DESTA expedicao. Antes, apagar
            // qualquer expedicao parava a trilha ativa de outra, que ficava
            // marcada como ativa sem gravar mais nada.
            stopTrackServiceIf { it.sessionId == session.id }
            if (_recording.value?.target?.sessionId == session.id) cancelRecording()
            val media = repository.getMediaForSession(session.id)
            repository.deleteSession(session)
            withContext(Dispatchers.IO) {
                media.forEach { mediaFiles.deleteIfManaged(it.filePath) }
                mediaFiles.deleteSessionFiles(session.projectId, session.id)
            }
            _message.value = "Expedição apagada."
            onDone()
        }
    }

    // ---------------------------------------------------------------- ocorrencias

    fun saveOccurrenceWithMedia(occurrence: OccurrenceEntity, pendingMedia: List<MediaEntity>, onSaved: (Long) -> Unit) {
        val error = validateCoordinates(occurrence.latitude, occurrence.longitude)
        if (error != null) {
            _message.value = error
            return
        }
        if (occurrence.latitude == null || occurrence.longitude == null) {
            _message.value = "Aviso: registro salvo sem coordenadas."
        } else if (occurrence.scientificName.isBlank()) {
            _message.value = "Aviso: nome da espécie é recomendado, mas o registro será salvo."
        }
        viewModelScope.launch {
            try {
                val id = if (occurrence.id == 0L) repository.createOccurrence(occurrence) else {
                    repository.updateOccurrence(occurrence)
                    occurrence.id
                }
                pendingMedia.forEach { media -> repository.createMedia(media.copy(id = 0L, occurrenceId = id)) }
                onSaved(id)
            } catch (_: SQLiteConstraintException) {
                _message.value = "occurrenceID já existe. Use um identificador único."
            }
        }
    }

    fun deleteOccurrence(occurrence: OccurrenceEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            val media = repository.getMediaForOccurrence(occurrence.id)
            repository.deleteOccurrence(occurrence)
            withContext(Dispatchers.IO) { media.forEach { mediaFiles.deleteIfManaged(it.filePath) } }
            _message.value = "Ocorrência apagada."
            onDone()
        }
    }

    /** Descarta as midias de um rascunho abandonado, para nao deixar arquivo orfao. */
    fun discardPendingMedia(media: List<MediaEntity>) {
        viewModelScope.launch(Dispatchers.IO) { media.forEach { mediaFiles.deleteIfManaged(it.filePath) } }
    }

    // ---------------------------------------------------------------- midias

    fun updateMedia(media: MediaEntity) {
        viewModelScope.launch {
            repository.updateMedia(media)
            _message.value = "Mídia atualizada."
        }
    }

    fun deleteMedia(media: MediaEntity) {
        viewModelScope.launch {
            repository.deleteMedia(media)
            withContext(Dispatchers.IO) { mediaFiles.deleteIfManaged(media.filePath) }
            _message.value = "Mídia removida."
        }
    }

    /** Midias pendentes que chegaram para um rascunho; o formulario as recolhe. */
    fun takePendingMedia(draftKey: String): List<MediaEntity> {
        val current = _pendingMedia.value[draftKey].orEmpty()
        if (current.isNotEmpty()) _pendingMedia.value = _pendingMedia.value - draftKey
        return current
    }

    private suspend fun deliver(target: MediaTarget, media: MediaEntity) {
        if (target.draftKey == null) {
            repository.createMedia(media)
        } else {
            val map = _pendingMedia.value
            _pendingMedia.value = map + (target.draftKey to (map[target.draftKey].orEmpty() + media))
        }
    }

    /**
     * Foto tirada pela camera. O EXIF e lido ANTES da recompressao: antes era
     * lido depois, quando data, GPS e camera ja tinham sido descartados.
     * Roda fora da main thread: uma foto de 12 MP travava a tela.
     */
    fun processCapturedPhoto(file: File, target: MediaTarget, quality: String, subject: String, description: String, onMetadata: (PhotoMetadata) -> Unit = {}) {
        viewModelScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                val meta = mediaFiles.readPhotoMetadata(file)
                mediaFiles.compressImage(file, quality)
                meta
            }
            onMetadata(metadata)
            deliver(target, mediaEntity(target, "photo", file, "image/jpeg", subject, description, metadata))
        }
    }

    fun importMedia(uri: Uri, type: String, target: MediaTarget, baseName: String, quality: String, subject: String, description: String, onMetadata: (PhotoMetadata) -> Unit = {}) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val mime = app.contentResolver.getType(uri) ?: if (type == "audio") "audio/wav" else "image/jpeg"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = mediaFiles.importUri(uri, target.projectId, target.sessionId, type, mime, baseName)
                    val metadata = if (type == "photo") mediaFiles.readPhotoMetadata(file).also { mediaFiles.compressImage(file, quality) } else null
                    file to metadata
                }
            }
            result.onSuccess { (file, metadata) ->
                metadata?.let(onMetadata)
                val storedMime = if (type == "photo") (if (file.extension.equals("png", true)) "image/png" else "image/jpeg") else mime
                deliver(target, mediaEntity(target, type, file, storedMime, subject, description, metadata))
            }.onFailure {
                _message.value = "Não foi possível importar o arquivo: ${it.message ?: "erro de leitura"}."
            }
        }
    }

    // ---------------------------------------------------------------- gravacao

    fun startRecording(target: MediaTarget, format: String, baseName: String, subject: String, description: String) {
        if (_recording.value != null) {
            _message.value = "Já existe uma gravação em andamento."
            return
        }
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _message.value = "Permissão de áudio negada."
            return
        }
        val file = mediaFiles.createAudioFile(target.projectId, target.sessionId, format, baseName)
        val sources = preferredAudioSources()
        val started = runCatching {
            if (format == "mp3") mp3Recorder.start(file, sources) else wavRecorder.start(file, sources)
        }
        started.onFailure {
            file.delete()
            _message.value = it.message ?: "Não foi possível iniciar a gravação de áudio."
            return
        }
        _recording.value = RecordingState(file.absolutePath, format, System.currentTimeMillis(), target, subject, description)
    }

    /**
     * Fonte de audio para bioacustica. A fonte MIC, usada ate a v0.2.0, aplica
     * controle automatico de ganho e reducao de ruido, que alteram amplitude e
     * espectro do sinal. UNPROCESSED entrega o sinal cru quando o aparelho
     * declara suporte; senao VOICE_RECOGNITION, que o Android exige sem ganho
     * automatico e sem reducao de ruido. MIC fica so como ultimo recurso.
     */
    private fun preferredAudioSources(): List<Int> {
        val audioManager = getApplication<Application>().getSystemService(AudioManager::class.java)
        val unprocessed = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) {
            listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        } else {
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }
    }

    fun recordingLevel(): Float = if (_recording.value?.format == "mp3") 0f else wavRecorder.level

    /**
     * Encerra e registra a gravacao. Chamado pelo botao Parar, ao sair da tela
     * ou quando o app vai para segundo plano (onde o Android 9+ entregaria
     * silencio no lugar do som).
     */
    fun stopRecording(reason: String? = null) {
        val state = _recording.value ?: return
        _recording.value = null
        viewModelScope.launch(NonCancellable) {
            val error = withContext(Dispatchers.IO) {
                if (state.format == "mp3") {
                    mp3Recorder.stop(); mp3Recorder.lastError
                } else {
                    wavRecorder.stop(); wavRecorder.lastError
                }
            }
            val file = File(state.filePath)
            if (!file.exists() || file.length() <= 44L) {
                _message.value = "A gravação não produziu áudio${error?.message?.let { ": $it" } ?: "."}"
                file.delete()
                return@launch
            }
            val mime = if (state.format == "mp3") "audio/mpeg" else "audio/wav"
            deliver(state.target, mediaEntity(state.target, "audio", file, mime, state.subject, state.description, null))
            _message.value = reason ?: "Áudio salvo."
        }
    }

    /** Rascunho descartado: a gravacao dele e cancelada e o arquivo apagado. */
    fun discardRecording(draftKey: String) {
        if (_recording.value?.target?.draftKey == draftKey) cancelRecording()
    }

    private fun cancelRecording() {
        val state = _recording.value ?: return
        _recording.value = null
        viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            if (state.format == "mp3") mp3Recorder.stop() else wavRecorder.stop()
            File(state.filePath).delete()
        }
    }

    override fun onCleared() {
        // Ultima garantia: nunca deixar o microfone aberto.
        if (_recording.value != null) {
            wavRecorder.stop()
            mp3Recorder.stop()
        }
        super.onCleared()
    }

    // ---------------------------------------------------------------- trilha

    fun addTrackPoint(trackId: Long, location: CapturedLocation) {
        viewModelScope.launch {
            val track = repository.getTrack(trackId) ?: return@launch
            recordTrackPoint(track, location, showMessage = true)
        }
    }

    private suspend fun recordTrackPoint(track: GpsTrackEntity, location: CapturedLocation, showMessage: Boolean): Boolean {
        if (!track.active) return false
        val previous = repository.getLastTrackPoint(track.id)
        val decision = TrackPointRules.decide(previous, location.latitude, location.longitude, location.accuracyMeters, location.capturedAt)
        if (!decision.accepted) {
            if (showMessage) _message.value = "Ponto ignorado: precisão baixa ou deslocamento insuficiente."
            return false
        }
        repository.addTrackPoint(
            GpsTrackPointEntity(
                trackId = track.id,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracyMeters,
                timestamp = location.capturedAt
            )
        )
        val latest = repository.getTrack(track.id) ?: track
        repository.updateTrack(
            latest.copy(
                totalDistance = latest.totalDistance + decision.distanceFromPrevious,
                duration = System.currentTimeMillis() - latest.startTime,
                pointsCount = repository.countTrackPoints(track.id)
            )
        )
        if (showMessage) _message.value = "Ponto da trilha salvo."
        return true
    }

    fun startTrack(session: SessionEntity, onStarted: (Long) -> Unit = {}) {
        if (!hasLocationPermission()) {
            _message.value = "Permita o acesso à localização para gravar a trilha."
            return
        }
        viewModelScope.launch {
            val existingActive = repository.getTracks(session.projectId).firstOrNull { it.sessionId == session.id && it.active }
            if (existingActive != null) {
                if (TrackRecordingService.runningTrackId != existingActive.id) startTrackService(existingActive.id)
                onStarted(existingActive.id)
                return@launch
            }
            val id = repository.createTrack(
                GpsTrackEntity(
                    projectId = session.projectId,
                    sessionId = session.id,
                    eventId = session.eventId,
                    coordinateFormat = "DD",
                    geodeticDatum = session.geodeticDatum,
                    active = true
                )
            )
            startTrackService(id)
            _message.value = "Trilha GPS iniciada. A linha será atualizada em tempo real."
            onStarted(id)
        }
    }

    fun stopTrack(track: GpsTrackEntity, finalLocation: CapturedLocation? = null) {
        viewModelScope.launch {
            if (finalLocation != null) recordTrackPoint(track, finalLocation, showMessage = false)
            val latest = repository.getTrack(track.id) ?: track
            val now = System.currentTimeMillis()
            repository.updateTrack(
                latest.copy(
                    endTime = now,
                    duration = now - latest.startTime,
                    active = false
                )
            )
            if (TrackRecordingService.runningTrackId == track.id || TrackRecordingService.runningTrackId == null) stopTrackService()
            _message.value = "Trilha GPS encerrada."
        }
    }

    /**
     * Trilha marcada como ativa no banco sem servico gravando (app fechado a
     * forca, aparelho reiniciado): retoma a gravacao em vez de deixar a tela
     * dizendo "ativa" enquanto nada e registrado.
     */
    private fun resumeInterruptedTracks() {
        viewModelScope.launch {
            val active = repository.getActiveTracks()
            val current = active.firstOrNull() ?: return@launch
            if (TrackRecordingService.runningTrackId == current.id) return@launch
            if (hasLocationPermission()) {
                startTrackService(current.id)
                _message.value = "Trilha GPS retomada após o app ter sido fechado."
            }
            // Mais de uma trilha ativa e estado invalido: so a mais recente segue.
            active.drop(1).forEach { stale ->
                repository.updateTrack(stale.copy(active = false, endTime = stale.endTime ?: System.currentTimeMillis()))
            }
        }
    }

    fun importTrack(session: SessionEntity, points: List<ImportedTrackPoint>) {
        viewModelScope.launch {
            val cleanPoints = points
                .filter { validateCoordinates(it.latitude, it.longitude) == null }
                .distinctBy { "${it.latitude},${it.longitude},${it.timestamp}" }
            if (cleanPoints.size < 2) {
                _message.value = "Não foi possível importar: a trilha precisa ter pelo menos 2 pontos válidos."
                return@launch
            }
            val trackId = repository.createTrack(
                GpsTrackEntity(
                    projectId = session.projectId,
                    sessionId = session.id,
                    eventId = session.eventId,
                    startTime = cleanPoints.first().timestamp,
                    endTime = cleanPoints.last().timestamp,
                    totalDistance = importedTrackDistance(cleanPoints),
                    duration = (cleanPoints.last().timestamp - cleanPoints.first().timestamp).coerceAtLeast(0L),
                    pointsCount = cleanPoints.size,
                    coordinateFormat = "DD",
                    geodeticDatum = session.geodeticDatum,
                    active = false
                )
            )
            cleanPoints.forEach { point ->
                repository.addTrackPoint(
                    GpsTrackPointEntity(
                        trackId = trackId,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        altitude = point.altitude,
                        accuracy = point.accuracy,
                        timestamp = point.timestamp
                    )
                )
            }
            _message.value = "Trilha importada com ${cleanPoints.size} pontos."
        }
    }

    private suspend fun stopTrackServiceIf(predicate: (GpsTrackEntity) -> Boolean) {
        val affected = repository.getActiveTracks().filter(predicate)
        if (affected.any { it.id == TrackRecordingService.runningTrackId }) stopTrackService()
    }

    private fun startTrackService(trackId: Long) {
        val context = getApplication<Application>()
        val intent = Intent(context, TrackRecordingService::class.java)
            .setAction(TrackRecordingService.ACTION_START)
            .putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
        runCatching { ContextCompat.startForegroundService(context, intent) }
            .onFailure { _message.value = "Não foi possível iniciar a trilha em segundo plano." }
    }

    private fun stopTrackService() {
        val context = getApplication<Application>()
        runCatching {
            context.startService(Intent(context, TrackRecordingService::class.java).setAction(TrackRecordingService.ACTION_STOP))
        }
    }

    private fun hasLocationPermission(): Boolean {
        val app = getApplication<Application>()
        return ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun importedTrackDistance(points: List<ImportedTrackPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    // ---------------------------------------------------------------- localizacao

    fun captureLocation(onResult: (CapturedLocation?) -> Unit) {
        viewModelScope.launch {
            val location = locationService.currentLocation(withAddress = reverseGeocodingEnabled)
            if (location == null) _message.value = "Sem posição de GPS agora. Tente em local mais aberto ou digite a coordenada."
            onResult(location)
        }
    }

    /** Posicao continua para o mapa; vazio se nao houver permissao. */
    fun locationUpdates(): Flow<CapturedLocation> =
        if (hasLocationPermission()) locationService.locationUpdates().catch { } else emptyFlow()

    // ---------------------------------------------------------------- exportacao

    fun exportProject(projectId: Long, format: ExportFormat, content: ExportContent, onReady: (Intent, String?) -> Unit) {
        runExport(onReady) {
            val project = repository.getProject(projectId) ?: return@runExport null
            exporter.export(
                format, project, repository.getSessions(projectId), repository.getOccurrences(projectId),
                repository.getMedia(projectId), repository.getFields(projectId), exportTracks(projectId), content,
                destination = mediaFiles::newExportFile
            )
        }
    }

    fun exportSession(projectId: Long, sessionId: Long, format: ExportFormat, content: ExportContent, onReady: (Intent, String?) -> Unit) {
        runExport(onReady) {
            val project = repository.getProject(projectId) ?: return@runExport null
            val session = repository.getSession(sessionId) ?: return@runExport null
            exporter.export(
                format,
                project,
                listOf(session),
                repository.getOccurrencesForSession(sessionId),
                repository.getMediaForSession(sessionId),
                repository.getFields(projectId),
                exportTracks(projectId).filter { it.eventID == session.eventId },
                content,
                "expedicao_${OikosExporter.safeName(session.name.ifBlank { session.eventId })}",
                destination = mediaFiles::newExportFile
            )
        }
    }

    /** Exportacao fora da main thread, com estado de andamento e sem execucao dupla. */
    private fun runExport(onReady: (Intent, String?) -> Unit, block: suspend () -> com.oikos.fieldbook.export.ExportResult?) {
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { runCatching { block() } }
                result.onSuccess { exported ->
                    if (exported != null) onReady(shareIntent(exported.file, exported.mimeType), exported.warning)
                }.onFailure {
                    _message.value = "A exportação falhou: ${it.message ?: it.javaClass.simpleName}. Libere espaço e tente de novo."
                }
            } finally {
                _exporting.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(message: String) {
        _message.value = message
    }

    private fun shareIntent(file: File, mimeType: String): Intent {
        val uri = mediaFiles.uriFor(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            putExtra(Intent.EXTRA_SUBJECT, "Exportacao Oikos FieldBook")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun validateCoordinates(latitude: Double?, longitude: Double?): String? = when {
        !CoordinateUtils.validLatitude(latitude) -> "Latitude deve estar entre -90 e 90."
        !CoordinateUtils.validLongitude(longitude) -> "Longitude deve estar entre -180 e 180."
        else -> null
    }

    private suspend fun exportTracks(projectId: Long): List<TrackExportBundle> =
        repository.getTracks(projectId).map { track ->
            TrackExportBundle(
                trackID = "TRK-${track.id.toString().padStart(6, '0')}",
                projectID = "PROJ-${track.projectId.toString().padStart(3, '0')}",
                eventID = track.eventId,
                startTime = track.startTime,
                endTime = track.endTime,
                totalDistance = track.totalDistance,
                duration = track.duration,
                pointsCount = track.pointsCount,
                coordinateFormat = track.coordinateFormat,
                geodeticDatum = track.geodeticDatum,
                points = repository.getTrackPoints(track.id).map {
                    TrackPointExport(it.latitude, it.longitude, it.altitude, it.accuracy, it.timestamp, it.speed)
                }
            )
        }

    private fun mediaEntity(
        target: MediaTarget,
        type: String,
        file: File,
        mimeType: String,
        subject: String,
        description: String,
        metadata: PhotoMetadata?
    ): MediaEntity = MediaEntity(
        projectId = target.projectId,
        sessionId = target.sessionId,
        occurrenceId = target.occurrenceId,
        mediaType = type,
        filePath = file.absolutePath,
        fileName = file.name,
        mimeType = mimeType,
        createdAt = metadata?.capturedAt ?: System.currentTimeMillis(),
        latitude = metadata?.latitude,
        longitude = metadata?.longitude,
        caption = description,
        mediaSubject = subject,
        notes = description
    )

    companion object {
        const val SETTINGS = "oikos_app_settings"
        private const val KEY_REVERSE_GEOCODING = "reverse_geocoding"

        fun newEventId(): String = "event-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}-${UUID.randomUUID().toString().take(8)}"
    }
}

data class ImportedTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracy: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)
