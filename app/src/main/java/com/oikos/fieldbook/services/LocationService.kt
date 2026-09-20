package com.oikos.fieldbook.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.Locale

data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Double?,
    val municipality: String = "",
    val stateProvince: String = "",
    val country: String = "",
    val capturedAt: Long = System.currentTimeMillis()
)

class LocationService(private val context: Context) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): CapturedLocation? {
        val captured = requestFreshLocation() ?: requestLastKnownLocation()
        return captured?.withAddress()
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): CapturedLocation? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { location -> continuation.resume(location?.toCapturedLocation()) }
                .addOnFailureListener { continuation.resume(null) }
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestLastKnownLocation(): CapturedLocation? =
        suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { location -> continuation.resume(location?.toCapturedLocation()) }
                .addOnFailureListener { continuation.resume(null) }
        }

    private fun android.location.Location.toCapturedLocation(): CapturedLocation = CapturedLocation(
        latitude = latitude,
        longitude = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
        capturedAt = time.takeIf { it > 0 } ?: System.currentTimeMillis()
    )

    private suspend fun CapturedLocation.withAddress(): CapturedLocation {
        val address = withContext(Dispatchers.IO) {
            runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }.getOrNull()
        } ?: return this
        return copy(
            municipality = address.locality.orEmpty().ifBlank { address.subAdminArea.orEmpty() },
            stateProvince = address.adminArea.orEmpty(),
            country = address.countryName.orEmpty()
        )
    }
}
