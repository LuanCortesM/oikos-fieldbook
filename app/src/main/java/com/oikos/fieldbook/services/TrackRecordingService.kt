package com.oikos.fieldbook.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.oikos.fieldbook.MainActivity
import com.oikos.fieldbook.OikosApplication
import com.oikos.fieldbook.R
import com.oikos.fieldbook.data.GpsTrackPointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TrackRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        ensureChannel()
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
        stopLocationUpdates()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(trackId: Long) {
        startForeground(NOTIFICATION_ID, notification("Trilha GPS ativa. Gravando mesmo com a tela bloqueada."))
        if (!hasLocationPermission()) {
            stopSelf()
            return
        }
        acquireWakeLock()
        stopLocationUpdates()
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) scope.launch { savePoint(trackId, location) }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(1_500L)
            .setMinUpdateDistanceMeters(1.5f)
            .setMaxUpdateDelayMillis(5_000L)
            .setWaitForAccurateLocation(false)
            .build()
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    scope.launch { savePoint(trackId, location) }
                }
            }
        }
        client.requestLocationUpdates(request, callback as LocationCallback, Looper.getMainLooper())
    }

    private suspend fun savePoint(trackId: Long, location: android.location.Location) {
        val repository = (application as OikosApplication).repository
        val track = repository.getTrack(trackId) ?: return
        if (!track.active) return

        val timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null
        val previous = repository.getLastTrackPoint(trackId)
        val decision = TrackPointRules.decide(previous, location.latitude, location.longitude, accuracy, timestamp)
        if (!decision.accepted) return

        repository.addTrackPoint(
            GpsTrackPointEntity(
                trackId = trackId,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = accuracy,
                timestamp = timestamp,
                speed = if (location.hasSpeed()) location.speed.toDouble() else null
            )
        )
        val pointCount = repository.countTrackPoints(trackId)
        val latest = repository.getTrack(trackId) ?: track
        val totalDistance = latest.totalDistance + decision.distanceFromPrevious
        repository.updateTrack(
            latest.copy(
                totalDistance = totalDistance,
                duration = System.currentTimeMillis() - latest.startTime,
                pointsCount = pointCount
            )
        )
        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                notification("Trilha ativa - $pointCount pontos - ${formatDistance(totalDistance)} - ${formatClock(timestamp)}")
            )
    }

    private fun stopRecording() {
        stopLocationUpdates()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopLocationUpdates() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
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

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Trilha GPS", NotificationManager.IMPORTANCE_LOW)
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
        const val ACTION_START = "com.oikos.fieldbook.START_TRACK"
        const val ACTION_STOP = "com.oikos.fieldbook.STOP_TRACK"
        const val EXTRA_TRACK_ID = "trackId"
        private const val CHANNEL_ID = "oikos_track_recording"
        private const val NOTIFICATION_ID = 37
    }
}

private fun formatDistance(distance: Double): String =
    if (distance < 1000) "${distance.toInt()} m" else String.format(java.util.Locale.US, "%.2f km", distance / 1000.0)

private fun formatClock(timestamp: Long): String =
    DateFormat.format("HH:mm:ss", timestamp).toString()
