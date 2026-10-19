package com.oikos.fieldbook.services

import com.oikos.fieldbook.util.AppLanguage
import com.oikos.fieldbook.util.CoordinateUtils
import java.util.Locale
import kotlin.math.ceil

/** Uma leitura do receptor durante a captura com media. */
data class GpsReading(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy: Double?,
    val time: Long
)

/** Resultado da media, pronto para o formulario e para a exportacao. */
data class AveragedFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    /** Mediana da precisao informada pelo receptor nas leituras usadas. */
    val reportedAccuracy: Double?,
    /** dwc:coordinateUncertaintyInMeters, ja com a regra do GBIF aplicada. */
    val uncertainty: Double,
    val received: Int,
    val used: Int,
    /** Maior distancia de uma leitura usada ate a media, em metros. */
    val spreadMeters: Double,
    val durationMillis: Long
) {
    /** Texto para dwc:georeferenceRemarks: como a coordenada foi obtida, no idioma do projeto. */
    fun remarks(language: AppLanguage = AppLanguage.PT): String {
        val spread = String.format(Locale.US, "%.1f m", spreadMeters)
        val seconds = durationMillis / 1000
        val accuracy = reportedAccuracy?.let { String.format(Locale.US, "%.1f m", it) }
        return when (language) {
            AppLanguage.EN ->
                "Mean of $used phone GNSS readings ($received received in $seconds s); " +
                    "receiver-reported accuracy (median) ${accuracy ?: "not reported"}; maximum spread $spread; " +
                    "uncertainty = max(2 × accuracy, spread)."
            AppLanguage.ES ->
                "Promedio de $used lecturas GNSS del celular (de $received recibidas en $seconds s); " +
                    "precisión informada por el receptor (mediana) ${accuracy ?: "no informada"}; dispersión máxima $spread; " +
                    "incertidumbre = máx(2 × precisión, dispersión)."
            AppLanguage.PT ->
                "Média de $used leituras GNSS do celular (de $received recebidas em $seconds s); " +
                    "precisão informada pelo receptor (mediana) ${accuracy ?: "não informada"}; dispersão máxima $spread; " +
                    "incerteza = máx(2 × precisão, dispersão)."
        }
    }
}

enum class FixQuality { GOOD, FAIR, POOR }

/**
 * Captura de ponto com media de leituras.
 *
 * Uma leitura isolada do GPS do celular pode errar muito, e usuarios de apps de
 * campo relatam nao saber quando a posicao "esta boa" (docs/pesquisa-apps-
 * semelhantes.md, secao 3). A media de varias leituras, descartando as piores,
 * e o semaforo de qualidade respondem a isso.
 *
 * Incerteza: o guia rapido de georreferenciamento do GBIF recomenda "a value
 * that is at least twice the value given by the GPS at the time the coordinates
 * were captured", e 30 m quando a precisao nao e conhecida (Zermoglio et al.
 * 2020, secao 1.6.15). Aqui: o maior entre 2 x a precisao informada e a
 * dispersao das leituras usadas, para que o circulo contenha todas elas.
 */
object GpsAveraging {
    const val GOOD_METERS = 10.0
    const val FAIR_METERS = 30.0
    const val UNKNOWN_ACCURACY_UNCERTAINTY = 30.0
    const val GBIF_PROTOCOL =
        "Zermoglio PF, Chapman AD, Wieczorek JR, Luna MC & Bloom DA (2020) Georeferencing Quick Reference Guide. " +
            "Copenhagen: GBIF Secretariat. https://doi.org/10.35035/e09p-h128"

    fun quality(accuracy: Double?): FixQuality = when {
        accuracy == null -> FixQuality.POOR
        accuracy <= GOOD_METERS -> FixQuality.GOOD
        accuracy <= FAIR_METERS -> FixQuality.FAIR
        else -> FixQuality.POOR
    }

    /** Incerteza para uma leitura unica, pela mesma regra do GBIF. */
    fun uncertaintyFor(reportedAccuracy: Double?): Double =
        reportedAccuracy?.let { ceil(2 * it - 1e-9).coerceAtLeast(1.0) } ?: UNKNOWN_ACCURACY_UNCERTAINTY

    fun average(readings: List<GpsReading>): AveragedFix? {
        if (readings.isEmpty()) return null
        val withAccuracy = readings.filter { it.accuracy != null }
        val best = withAccuracy.minOfOrNull { it.accuracy!! }
        // Descarta leituras muito piores que a melhor (salto de rede, multipercurso).
        val kept = if (best == null) readings else withAccuracy.filter { it.accuracy!! <= maxOf(best * 2, GOOD_METERS) }
        val latitude = kept.map { it.latitude }.average()
        val longitude = kept.map { it.longitude }.average()
        val altitudes = kept.mapNotNull { it.altitude }
        val reported = median(kept.mapNotNull { it.accuracy })
        val spread = kept.maxOf { distanceMeters(latitude, longitude, it.latitude, it.longitude) }
        val gbif = reported?.let { 2 * it } ?: UNKNOWN_ACCURACY_UNCERTAINTY
        return AveragedFix(
            latitude = CoordinateUtils.roundCoordinate(latitude),
            longitude = CoordinateUtils.roundCoordinate(longitude),
            altitude = CoordinateUtils.roundAltitude(altitudes.takeIf { it.isNotEmpty() }?.average()),
            reportedAccuracy = reported?.let { Math.round(it * 10.0) / 10.0 },
            uncertainty = ceil(maxOf(gbif, spread) - 1e-9).coerceAtLeast(1.0),
            received = readings.size,
            used = kept.size,
            spreadMeters = Math.round(spread * 10.0) / 10.0,
            durationMillis = (readings.maxOf { it.time } - readings.minOf { it.time }).coerceAtLeast(0L)
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
