package com.oikos.fieldbook.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.oikos.fieldbook.util.CoordinateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    /**
     * Posicao atual por requisicao ativa de alta acuracia.
     *
     * A ultima posicao em cache so e aceita se tiver no maximo
     * [MAX_CACHED_AGE_MILLIS]: antes ela entrava sem aviso e podia ser de horas
     * atras e quilometros de distancia.
     *
     * [withAddress] consulta o Geocoder do sistema, que envia a coordenada pela
     * internet ao servico de enderecos do Google. Por isso so roda quando o
     * usuario liga essa opcao nas configuracoes.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(withAddress: Boolean): CapturedLocation? {
        val captured = requestFreshLocation() ?: requestRecentCachedLocation()
        return if (withAddress) captured?.withAddress() else captured
    }

    /**
     * Atualizacoes continuas para o mapa enquanto ele esta na tela. Substitui a
     * captura repetida a cada 3 s, que fazia geocodificacao a cada leitura.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMillis: Long = 3_000L): Flow<CapturedLocation> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toCapturedLocation()) }
            }
        }
        val registered = runCatching {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }.isSuccess
        if (!registered) close()
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): CapturedLocation? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            continuation.invokeOnCancellation { cancellation.cancel() }
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .addOnSuccessListener { location -> continuation.resume(location?.toCapturedLocation()) }
                    .addOnFailureListener { continuation.resume(null) }
            }.onFailure { continuation.resume(null) }
        }

    @SuppressLint("MissingPermission")
    private suspend fun requestRecentCachedLocation(): CapturedLocation? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                client.lastLocation
                    .addOnSuccessListener { location ->
                        val recent = location?.takeIf { System.currentTimeMillis() - it.time <= MAX_CACHED_AGE_MILLIS }
                        continuation.resume(recent?.toCapturedLocation())
                    }
                    .addOnFailureListener { continuation.resume(null) }
            }.onFailure { continuation.resume(null) }
        }

    private fun android.location.Location.toCapturedLocation(): CapturedLocation = CapturedLocation(
        latitude = CoordinateUtils.roundCoordinate(latitude),
        longitude = CoordinateUtils.roundCoordinate(longitude),
        altitude = if (hasAltitude()) CoordinateUtils.roundAltitude(altitude) else null,
        accuracyMeters = if (hasAccuracy()) CoordinateUtils.roundUncertainty(accuracy.toDouble()) else null,
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

    companion object {
        const val MAX_CACHED_AGE_MILLIS = 120_000L
    }
}
