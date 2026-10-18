package com.oikos.fieldbook.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.math.tan

object CoordinateUtils {
    private const val WGS84_A = 6378137.0
    private const val WGS84_F = 1.0 / 298.257223563
    private const val UTM_K0 = 0.9996
    private const val FALSE_EASTING = 500000.0
    private const val FALSE_NORTHING = 10000000.0

    private val HEMISPHERE_WORDS = listOf(
        "NORTE" to "N", "NORTH" to "N", "SUL" to "S", "SOUTH" to "S",
        "LESTE" to "L", "EAST" to "E", "OESTE" to "O", "WEST" to "W"
    )

    fun validLatitude(value: Double?): Boolean = value == null || value in -90.0..90.0
    fun validLongitude(value: Double?): Boolean = value == null || value in -180.0..180.0

    /**
     * Arredondamentos da leitura do GPS.
     *
     * O receptor devolve float convertido para double, e o ruido da conversao
     * virava "12.302000045776367 m" de incerteza na exportacao: falsa precisao,
     * contra o que as boas praticas de georreferenciamento do GBIF recomendam.
     * Coordenada com 7 casas (~1 cm, abaixo de qualquer GNSS de celular),
     * incerteza arredondada para cima em metro inteiro (nunca subestima o erro)
     * e altitude com 1 casa.
     */
    fun roundCoordinate(value: Double): Double = Math.round(value * 1e7) / 1e7
    fun roundUncertainty(value: Double?): Double? = value?.let { kotlin.math.ceil(it - 1e-9).coerceAtLeast(1.0) }
    fun roundAltitude(value: Double?): Double? = value?.let { Math.round(it * 10.0) / 10.0 }

    /**
     * Le coordenada em graus/minutos/segundos ou graus e minutos decimais,
     * como se anota em caderneta ou se le em GPS de mao:
     * 22°25'51.6"S, 22 25 51.6 S, S 22 25.86, -22 25.86, 44°58'44"O.
     * Aceita N/S/E/W e tambem L (leste) e O (oeste), ou as palavras (Sul,
     * Oeste...). Virgula decimal tambem.
     *
     * O hemisferio (letra ou sinal de menos) e obrigatorio: sem ele, "22 25 51.6"
     * virava +22,43, um ponto no Hemisferio Norte, sem nenhum aviso.
     */
    fun parseSexagesimal(text: String, axis: Axis): Double? {
        var raw = text.trim().uppercase(Locale.ROOT).replace(',', '.')
        if (raw.isBlank()) return null
        HEMISPHERE_WORDS.forEach { (word, letter) -> raw = raw.replace(Regex("\\b$word\\b"), letter) }
        val hemisphere = Regex("[NSEWLO]").findAll(raw).map { it.value }.toList()
        if (hemisphere.size > 1) return null
        val letter = hemisphere.firstOrNull()
        val validLetters = if (axis == Axis.Latitude) setOf("N", "S") else setOf("E", "W", "L", "O")
        if (letter != null && letter !in validLetters) return null
        val minus = raw.trimStart().startsWith("-")
        if (letter == null && !minus) return null
        if (minus && letter in setOf("N", "E", "L")) return null
        val negative = minus || letter in setOf("S", "W", "O")
        val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(raw).map { it.value.toDouble() }.toList()
        if (numbers.isEmpty() || numbers.size > 3) return null
        val degrees = numbers[0]
        val minutes = numbers.getOrElse(1) { 0.0 }
        val seconds = numbers.getOrElse(2) { 0.0 }
        if (minutes >= 60.0 || seconds >= 60.0) return null
        if (numbers.size > 1 && degrees % 1.0 != 0.0) return null
        if (numbers.size > 2 && minutes % 1.0 != 0.0) return null
        val value = degrees + minutes / 60.0 + seconds / 3600.0
        val limit = if (axis == Axis.Latitude) 90.0 else 180.0
        if (value > limit) return null
        return roundCoordinate(if (negative) -value else value)
    }

    /**
     * Coordenada como apresentada no formato escolhido pelo projeto, para
     * dwc:verbatimCoordinates. Devolve o par e o nome do sistema conforme os
     * exemplos de dwc:verbatimCoordinateSystem.
     */
    fun verbatim(latitude: Double?, longitude: Double?, format: String): Pair<String, String>? {
        if (latitude == null || longitude == null) return null
        return when (format) {
            "DMS" -> "${toDms(latitude, Axis.Latitude)} ${toDms(longitude, Axis.Longitude)}" to "degrees minutes seconds"
            "DMM" -> "${toDmm(latitude, Axis.Latitude)} ${toDmm(longitude, Axis.Longitude)}" to "degrees decimal minutes"
            else -> null
        }
    }

    fun format(value: Double?, axis: Axis, format: String): String {
        if (value == null) return ""
        return when (format) {
            "DMS" -> toDms(value, axis)
            "DMM" -> toDmm(value, axis)
            else -> String.format(Locale.US, "%.6f", value)
        }
    }

    /**
     * Zona UTM, com as excecoes da Noruega (32V) e de Svalbard (31X a 37X).
     * Longitude 180 fica na zona 60: antes dava 61, que nao existe.
     */
    fun utmZone(latitude: Double, longitude: Double): Int {
        var zone = (((longitude + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)
        if (latitude >= 56.0 && latitude < 64.0 && longitude >= 3.0 && longitude < 12.0) zone = 32
        if (latitude >= 72.0 && latitude <= 84.0) {
            zone = when {
                longitude >= 0.0 && longitude < 9.0 -> 31
                longitude >= 9.0 && longitude < 21.0 -> 33
                longitude >= 21.0 && longitude < 33.0 -> 35
                longitude >= 33.0 && longitude < 42.0 -> 37
                else -> zone
            }
        }
        return zone
    }

    /**
     * Letra da faixa de latitude UTM (C a X, sem I e O), de 8 em 8 graus a
     * partir de 80 S; a faixa X vai de 72 a 84 N. Fora disso (regioes polares,
     * sistema UPS) nao ha faixa.
     */
    fun utmBand(latitude: Double): String {
        if (latitude < -80.0 || latitude > 84.0) return ""
        val bands = "CDEFGHJKLMNPQRSTUVWX"
        val index = ((latitude + 80.0) / 8.0).toInt().coerceIn(0, bands.length - 1)
        return bands[index].toString()
    }

    fun toUtm(latitude: Double?, longitude: Double?): UtmCoordinate? {
        if (latitude == null || longitude == null || !validLatitude(latitude) || !validLongitude(longitude)) return null
        val zone = utmZone(latitude, longitude)
        val hemisphere = if (latitude < 0) UtmHemisphere.South else UtmHemisphere.North
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)
        val lonOrigin = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val eccentricitySquared = WGS84_F * (2.0 - WGS84_F)
        val secondEccentricitySquared = eccentricitySquared / (1.0 - eccentricitySquared)
        val n = WGS84_A / sqrt(1.0 - eccentricitySquared * sin(latRad).pow(2.0))
        val t = tan(latRad).pow(2.0)
        val c = secondEccentricitySquared * cos(latRad).pow(2.0)
        val a = cos(latRad) * (lonRad - lonOrigin)
        val meridionalArc = WGS84_A * (
            (1 - eccentricitySquared / 4 - 3 * eccentricitySquared.pow(2.0) / 64 - 5 * eccentricitySquared.pow(3.0) / 256) * latRad
                - (3 * eccentricitySquared / 8 + 3 * eccentricitySquared.pow(2.0) / 32 + 45 * eccentricitySquared.pow(3.0) / 1024) * sin(2 * latRad)
                + (15 * eccentricitySquared.pow(2.0) / 256 + 45 * eccentricitySquared.pow(3.0) / 1024) * sin(4 * latRad)
                - (35 * eccentricitySquared.pow(3.0) / 3072) * sin(6 * latRad)
            )
        val easting = UTM_K0 * n * (
            a + (1 - t + c) * a.pow(3.0) / 6 +
                (5 - 18 * t + t.pow(2.0) + 72 * c - 58 * secondEccentricitySquared) * a.pow(5.0) / 120
            ) + FALSE_EASTING
        var northing = UTM_K0 * (
            meridionalArc + n * tan(latRad) * (
                a.pow(2.0) / 2 + (5 - t + 9 * c + 4 * c.pow(2.0)) * a.pow(4.0) / 24 +
                    (61 - 58 * t + t.pow(2.0) + 600 * c - 330 * secondEccentricitySquared) * a.pow(6.0) / 720
                )
            )
        if (hemisphere == UtmHemisphere.South) northing += FALSE_NORTHING
        return UtmCoordinate(zone, hemisphere, easting, northing)
    }

    fun fromUtm(zone: Int, hemisphere: UtmHemisphere, easting: Double, northing: Double): LatLon? {
        if (zone !in 1..60 || easting !in 100000.0..900000.0 || northing !in 0.0..10000000.0) return null
        val eccentricitySquared = WGS84_F * (2.0 - WGS84_F)
        val e1 = (1.0 - sqrt(1.0 - eccentricitySquared)) / (1.0 + sqrt(1.0 - eccentricitySquared))
        val secondEccentricitySquared = eccentricitySquared / (1.0 - eccentricitySquared)
        val x = easting - FALSE_EASTING
        var y = northing
        if (hemisphere == UtmHemisphere.South) y -= FALSE_NORTHING
        val lonOrigin = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val m = y / UTM_K0
        val mu = m / (WGS84_A * (1 - eccentricitySquared / 4 - 3 * eccentricitySquared.pow(2.0) / 64 - 5 * eccentricitySquared.pow(3.0) / 256))
        val phi1 = mu +
            (3 * e1 / 2 - 27 * e1.pow(3.0) / 32) * sin(2 * mu) +
            (21 * e1.pow(2.0) / 16 - 55 * e1.pow(4.0) / 32) * sin(4 * mu) +
            (151 * e1.pow(3.0) / 96) * sin(6 * mu) +
            (1097 * e1.pow(4.0) / 512) * sin(8 * mu)
        val n1 = WGS84_A / sqrt(1 - eccentricitySquared * sin(phi1).pow(2.0))
        val t1 = tan(phi1).pow(2.0)
        val c1 = secondEccentricitySquared * cos(phi1).pow(2.0)
        val r1 = WGS84_A * (1 - eccentricitySquared) / (1 - eccentricitySquared * sin(phi1).pow(2.0)).pow(1.5)
        val d = x / (n1 * UTM_K0)
        val lat = phi1 - (n1 * tan(phi1) / r1) * (
            d.pow(2.0) / 2 -
                (5 + 3 * t1 + 10 * c1 - 4 * c1.pow(2.0) - 9 * secondEccentricitySquared) * d.pow(4.0) / 24 +
                (61 + 90 * t1 + 298 * c1 + 45 * t1.pow(2.0) - 252 * secondEccentricitySquared - 3 * c1.pow(2.0)) * d.pow(6.0) / 720
            )
        val lon = lonOrigin + (
            d - (1 + 2 * t1 + c1) * d.pow(3.0) / 6 +
                (5 - 2 * c1 + 28 * t1 - 3 * c1.pow(2.0) + 8 * secondEccentricitySquared + 24 * t1.pow(2.0)) * d.pow(5.0) / 120
            ) / cos(phi1)
        val result = LatLon(Math.toDegrees(lat), Math.toDegrees(lon))
        return if (validLatitude(result.latitude) && validLongitude(result.longitude)) result else null
    }

    // Arredonda no total (centesimos de segundo / milesimos de minuto) antes de
    // separar as partes; senao 59.996" vira "60.00" e o minuto nao sobe.
    fun toDms(value: Double, axis: Axis): String {
        val hemisphere = hemisphere(value, axis)
        val centiseconds = Math.round(abs(value) * 360_000.0)
        val degrees = centiseconds / 360_000
        val minutes = (centiseconds % 360_000) / 6_000
        val seconds = (centiseconds % 6_000) / 100.0
        return String.format(Locale.US, "%d°%02d'%05.2f\"%s", degrees, minutes, seconds, hemisphere)
    }

    fun toDmm(value: Double, axis: Axis): String {
        val hemisphere = hemisphere(value, axis)
        val hundredThousandthMinutes = Math.round(abs(value) * 6_000_000.0)
        val degrees = hundredThousandthMinutes / 6_000_000
        val minutes = (hundredThousandthMinutes % 6_000_000) / 100_000.0
        return String.format(Locale.US, "%d°%08.5f'%s", degrees, minutes, hemisphere)
    }

    private fun hemisphere(value: Double, axis: Axis): String =
        when (axis) {
            Axis.Latitude -> if (value < 0) "S" else "N"
            Axis.Longitude -> if (value < 0) "W" else "E"
        }
}

enum class Axis {
    Latitude,
    Longitude
}

enum class UtmHemisphere {
    North,
    South
}

data class UtmCoordinate(
    val zone: Int,
    val hemisphere: UtmHemisphere,
    val easting: Double,
    val northing: Double
)

data class LatLon(
    val latitude: Double,
    val longitude: Double
)
