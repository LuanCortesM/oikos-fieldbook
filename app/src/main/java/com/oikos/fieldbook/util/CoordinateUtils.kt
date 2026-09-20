package com.oikos.fieldbook.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
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

    fun validLatitude(value: Double?): Boolean = value == null || value in -90.0..90.0
    fun validLongitude(value: Double?): Boolean = value == null || value in -180.0..180.0

    fun format(value: Double?, axis: Axis, format: String): String {
        if (value == null) return ""
        return when (format) {
            "DMS" -> toDms(value, axis)
            "DMM" -> toDmm(value, axis)
            else -> String.format(Locale.US, "%.6f", value)
        }
    }

    fun toUtm(latitude: Double?, longitude: Double?): UtmCoordinate? {
        if (latitude == null || longitude == null || !validLatitude(latitude) || !validLongitude(longitude)) return null
        val zone = ((longitude + 180.0) / 6.0).toInt() + 1
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

    private fun toDms(value: Double, axis: Axis): String {
        val hemisphere = hemisphere(value, axis)
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val minutesFloat = (absolute - degrees) * 60.0
        val minutes = floor(minutesFloat).toInt()
        val seconds = (minutesFloat - minutes) * 60.0
        return String.format(Locale.US, "%d°%02d'%05.2f\"%s", degrees, minutes, seconds, hemisphere)
    }

    private fun toDmm(value: Double, axis: Axis): String {
        val hemisphere = hemisphere(value, axis)
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val minutes = (absolute - degrees) * 60.0
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
