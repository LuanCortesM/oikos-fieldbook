package com.oikos.fieldbook.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.oikos.fieldbook.util.CoordinateUtils
import com.oikos.fieldbook.geo.Geoid
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.oikos.fieldbook.AppViewModel
import com.oikos.fieldbook.MainActivity
import com.oikos.fieldbook.OikosApplication
import com.oikos.fieldbook.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class TrackRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    /** Alternativa sem Google Play Services (Huawei e outros): GPS direto do sistema. */
    private var systemListener: LocationListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Posicoes processadas uma de cada vez, na ordem em que chegam. Antes cada
     * posicao virava uma tarefa paralela: no modo economia o GPS entrega varias
     * de uma vez, e elas liam o mesmo ponto anterior e se sobrescreviam.
     */
    private val positions = Channel<Pair<Long, Location>>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        ensureChannel()
        scope.launch {
            for ((trackId, location) in positions) {
                runCatching { savePoint(trackId, location) }.onFailure(::reportSaveFailure)
            }
        }
    }

    private var lastFailureNotice = 0L

    /**
     * Ponto que o banco recusou (disco cheio): antes a falha era engolida e a
     * notificacao seguia dizendo "trilha ativa" sem gravar nada.
     */
    private fun reportSaveFailure(error: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastFailureNotice < FAILURE_NOTICE_INTERVAL_MILLIS) return
        lastFailureNotice = now
        notifyProblem(
            if (error is android.database.sqlite.SQLiteFullException) {
                t("Trilha GPS: sem espaço no aparelho, os pontos não estão sendo salvos. Libere espaço.", "GPS track: no space left on the device, points are not being saved. Free up space.")
            } else {
                val reason = error.message ?: error.javaClass.simpleName
                t("Trilha GPS: falha ao salvar pontos ($reason).", "GPS track: could not save points ($reason).")
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            ACTION_START -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, 0L)
                if (trackId > 0L) startRecording(trackId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runningTrackId = null
        stopLocationUpdates()
        releaseWakeLock()
        positions.close()
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(trackId: Long) {
        // O servico precisa entrar em primeiro plano logo apos o
        // startForegroundService, mas no Android 14+ declarar o tipo location
        // sem a permissao lanca SecurityException e derruba o app. Sem
        // permissao: entra em primeiro plano sem tipo e encerra em seguida.
        val started = runCatching {
            if (!hasLocationPermission()) error("sem permissão de localização")
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(t("Trilha GPS ativa. Gravando mesmo com a tela bloqueada.", "GPS track active. Recording even with the screen locked.")),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
        }.isSuccess
        if (!started) {
            // Aviso separado e que fica: a notificacao do servico some ao parar,
            // e antes o pesquisador nunca ficava sabendo que a trilha nao gravou.
            notifyProblem(t("Trilha GPS não iniciada: o Oikos está sem permissão de localização.", "GPS track not started: Oikos has no location permission."))
            runCatching { startForeground(NOTIFICATION_ID, notification(t("Trilha GPS interrompida.", "GPS track stopped."))) }
            stopRecording()
            return
        }
        runningTrackId = trackId
        acquireWakeLock()
        stopLocationUpdates()
        val profile = TrackProfile.parse(
            getSharedPreferences(AppViewModel.SETTINGS, MODE_PRIVATE).getString(TrackProfile.PREFERENCE_KEY, null)
        )
        if (hasPlayServices()) {
            client.lastLocation.addOnSuccessListener { location ->
                // A ultima posicao conhecida pode ser de horas atras e de outro
                // lugar: so entra se for recente.
                if (location != null && System.currentTimeMillis() - location.time in 0..LAST_LOCATION_MAX_AGE_MILLIS) {
                    positions.trySend(trackId to location)
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, profile.intervalMillis)
                .setMinUpdateIntervalMillis(profile.minIntervalMillis)
                .setMinUpdateDistanceMeters(profile.minDistanceMeters)
                .setMaxUpdateDelayMillis(profile.maxDelayMillis)
                .setWaitForAccurateLocation(false)
                .build()
            val fused = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { location -> positions.trySend(trackId to location) }
                }
            }
            callback = fused
            client.requestLocationUpdates(request, fused, Looper.getMainLooper())
                .addOnFailureListener { useSystemGps(trackId, profile) }
        } else {
            useSystemGps(trackId, profile)
        }
    }

    @SuppressLint("MissingPermission")
    private fun useSystemGps(trackId: Long, profile: TrackProfile) {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        val manager = getSystemService(LocationManager::class.java)
        if (manager == null || !manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            notifyProblem(t("Trilha GPS sem sinal: o GPS do aparelho está desligado.", "GPS track without signal: the device GPS is turned off."))
            return
        }
        val listener = LocationListener { location -> positions.trySend(trackId to location) }
        systemListener = listener
        runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, profile.intervalMillis, profile.minDistanceMeters, listener, Looper.getMainLooper())
        }.onFailure { notifyProblem(t("Trilha GPS sem sinal: não foi possível ligar o GPS do aparelho.", "GPS track without signal: could not turn on the device GPS.")) }
    }

    private fun hasPlayServices(): Boolean =
        runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS }.getOrDefault(false)

    private suspend fun savePoint(trackId: Long, location: Location) {
        if (runningTrackId != trackId) return
        val repository = (application as OikosApplication).repository
        val timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) CoordinateUtils.roundUncertainty(location.accuracy.toDouble()) else null
        val recorded = repository.recordTrackPoint(
            trackId = trackId,
            latitude = CoordinateUtils.roundCoordinate(location.latitude),
            longitude = CoordinateUtils.roundCoordinate(location.longitude),
            // Sobre o nivel do mar (geoide EGM2008), como na captura de ponto.
            altitude = if (location.hasAltitude()) Geoid.get(this)?.let { CoordinateUtils.roundAltitude(it.aboveSeaLevel(location.altitude, location.latitude, location.longitude)) } else null,
            accuracy = accuracy,
            timestamp = timestamp,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null
        ) ?: return
        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                notification(
                    t("Trilha ativa - ${recorded.pointsCount} pontos", "Track active - ${recorded.pointsCount} points") +
                        " - ${formatDistance(recorded.totalDistance)} - ${formatClock(timestamp)}"
                )
            )
    }

    private fun stopRecording() {
        runningTrackId = null
        stopLocationUpdates()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopLocationUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        systemListener?.let { listener -> runCatching { getSystemService(LocationManager::class.java)?.removeUpdates(listener) } }
        systemListener = null
    }

    private fun notifyProblem(text: String) {
        val notice = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_oikos_notification)
            .setContentTitle("Oikos FieldBook")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(PROBLEM_NOTIFICATION_ID, notice) }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OikosFieldBook:TrackRecording").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun t(pt: String, en: String): String = com.oikos.fieldbook.util.UiLanguage.t(pt, en)

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, t("Trilha GPS", "GPS track"), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_oikos_notification)
            .setContentTitle("Oikos FieldBook")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    companion object {
        /**
         * Trilha sendo gravada neste processo, ou null. Permite ao app saber se
         * uma trilha marcada como ativa no banco perdeu o servico (app fechado a
         * forca, aparelho reiniciado) e retoma-la.
         */
        @Volatile
        var runningTrackId: Long? = null
            private set

        const val ACTION_START = "com.oikos.fieldbook.START_TRACK"
        const val ACTION_STOP = "com.oikos.fieldbook.STOP_TRACK"
        const val EXTRA_TRACK_ID = "trackId"
        private const val CHANNEL_ID = "oikos_track_recording"
        private const val NOTIFICATION_ID = 37
        private const val PROBLEM_NOTIFICATION_ID = 38
        private const val FAILURE_NOTICE_INTERVAL_MILLIS = 10 * 60 * 1000L
        private const val LAST_LOCATION_MAX_AGE_MILLIS = 30_000L
    }
}

private fun formatDistance(distance: Double): String =
    if (distance < 1000) "${distance.toInt()} m" else String.format(java.util.Locale.US, "%.2f km", distance / 1000.0)

private fun formatClock(timestamp: Long): String =
    DateFormat.format("HH:mm:ss", timestamp).toString()
