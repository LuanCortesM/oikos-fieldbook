package com.oikos.fieldbook.services

import com.oikos.fieldbook.data.GpsTrackPointEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackPointDecision(
    val accepted: Boolean,
    val distanceFromPrevious: Double = 0.0
)

object TrackPointRules {
    private const val MAX_FIRST_ACCURACY_METERS = 75.0
    private const val MAX_ACCURACY_METERS = 45.0
    private const val MIN_DISTANCE_METERS = 1.5
    private const val MIN_TIME_MILLIS = 2_500L
    private const val FORCE_KEEP_AFTER_MILLIS = 30_000L
    private const val MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND = 12.0

    fun decide(
        previous: GpsTrackPointEntity?,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
        timestamp: Long
    ): TrackPointDecision {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return TrackPointDecision(false)
        val accuracy = accuracyMeters ?: Double.POSITIVE_INFINITY
        if (previous == null) return TrackPointDecision(accuracy <= MAX_FIRST_ACCURACY_METERS)
        if (accuracy > MAX_ACCURACY_METERS) return TrackPointDecision(false)

        val elapsed = (timestamp - previous.timestamp).coerceAtLeast(0L)
        val distance = distanceMeters(previous.latitude, previous.longitude, latitude, longitude)
        if (distance < MIN_DISTANCE_METERS && elapsed < FORCE_KEEP_AFTER_MILLIS) return TrackPointDecision(false, distance)
        if (elapsed < MIN_TIME_MILLIS && distance < MIN_DISTANCE_METERS * 2) return TrackPointDecision(false, distance)

        val seconds = elapsed / 1000.0
        if (seconds > 0 && distance / seconds > MAX_PLAUSIBLE_SPEED_METERS_PER_SECOND && accuracy > 15.0) {
            return TrackPointDecision(false, distance)
        }
        // Ponto guardado so para marcar o tempo (parado): nao soma tremor do GPS a distancia.
        return TrackPointDecision(true, if (distance < MIN_DISTANCE_METERS) 0.0 else distance)
    }
}

fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
}
