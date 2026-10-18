package com.oikos.fieldbook.services

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Ponto lido de um arquivo de trilha; [timestamp] nulo = o arquivo nao traz horario. */
data class ParsedTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val timestamp: Long? = null
)

/**
 * Leitura de trilhas GPX, KML, GeoJSON e CSV, sem dependencia de Android.
 *
 * Horario so entra quando o arquivo o traz. Antes, pontos de KML, GeoJSON e
 * CSV recebiam o instante da importacao (e os de GPX sem horario, a
 * importacao mais um milissegundo por ponto): a trilha parecia ter sido
 * percorrida no dia da importacao, em zero segundos.
 */
object TrackFileParser {

    fun parse(text: String): List<ParsedTrackPoint> {
        if (text.isBlank()) return emptyList()
        return when {
            text.contains("<gpx", ignoreCase = true) -> parseGpx(text)
            text.contains("<kml", ignoreCase = true) || text.contains("<coordinates", ignoreCase = true) || text.contains("<gx:coord", ignoreCase = true) -> parseKml(text)
            text.trimStart().startsWith("{") -> parseGeoJson(text)
            else -> parseCsv(text)
        }
    }

    // ------------------------------------------------------------------ GPX

    /**
     * Pontos da trilha (trkpt); sem trilha, da rota (rtept); sem rota, os
     * waypoints. Antes os tres tipos eram misturados numa linha so, o atributo
     * lat precisava vir antes de lon e o ponto sem filhos (<trkpt .../>) era
     * ignorado.
     */
    private fun parseGpx(text: String): List<ParsedTrackPoint> =
        gpxPoints(text, "trkpt").ifEmpty { gpxPoints(text, "rtept") }.ifEmpty { gpxPoints(text, "wpt") }

    private fun gpxPoints(text: String, tag: String): List<ParsedTrackPoint> {
        val element = Regex("""<$tag\b([^>]*?)(?:/>|>(.*?)</$tag\s*>)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val elevation = Regex("""<ele>\s*([-+0-9.eE]+)\s*</ele>""", RegexOption.IGNORE_CASE)
        val time = Regex("""<time>\s*([^<]+?)\s*</time>""", RegexOption.IGNORE_CASE)
        return element.findAll(text).mapNotNull { match ->
            val attributes = match.groupValues[1]
            val body = match.groupValues.getOrNull(2).orEmpty()
            val lat = attribute(attributes, "lat")?.toDoubleOrNull()
            val lon = attribute(attributes, "lon")?.toDoubleOrNull()
            if (lat == null || lon == null) return@mapNotNull null
            ParsedTrackPoint(
                latitude = lat,
                longitude = lon,
                altitude = elevation.find(body)?.groupValues?.get(1)?.toDoubleOrNull(),
                timestamp = time.find(body)?.groupValues?.get(1)?.let(::parseInstant)
            )
        }.toList()
    }

    private fun attribute(attributes: String, name: String): String? =
        Regex("""\b$name\s*=\s*["']\s*([-+0-9.eE]+)\s*["']""", RegexOption.IGNORE_CASE).find(attributes)?.groupValues?.get(1)

    // ------------------------------------------------------------------ KML

    /** LineString/Point (<coordinates>) e trilha com horario do Google Earth (<gx:Track>). */
    private fun parseKml(text: String): List<ParsedTrackPoint> {
        if (text.contains("<gx:coord", ignoreCase = true)) {
            val whens = Regex("""<when>\s*([^<]+?)\s*</when>""", RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1] }.toList()
            val coords = Regex("""<gx:coord>\s*([^<]+?)\s*</gx:coord>""", RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1] }.toList()
            val points = coords.mapIndexedNotNull { index, coord ->
                val parts = coord.trim().split(Regex("\\s+"))
                val lon = parts.getOrNull(0)?.toDoubleOrNull()
                val lat = parts.getOrNull(1)?.toDoubleOrNull()
                if (lat == null || lon == null) null
                else ParsedTrackPoint(lat, lon, parts.getOrNull(2)?.toDoubleOrNull(), if (whens.size == coords.size) parseInstant(whens[index]) else null)
            }
            if (points.isNotEmpty()) return points
        }
        return Regex("""<coordinates[^>]*>(.*?)</coordinates>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(text)
            .flatMap { block ->
                block.groupValues[1].trim().split(Regex("\\s+")).asSequence().mapNotNull { token ->
                    val parts = token.split(",")
                    val lon = parts.getOrNull(0)?.toDoubleOrNull()
                    val lat = parts.getOrNull(1)?.toDoubleOrNull()
                    if (lat != null && lon != null) ParsedTrackPoint(lat, lon, parts.getOrNull(2)?.toDoubleOrNull()) else null
                }
            }
            .toList()
    }

    // -------------------------------------------------------------- GeoJSON

    /** Coordenadas [lon, lat, alt]; horarios de "coordTimes" (togeojson, Strava) quando existem. */
    private fun parseGeoJson(text: String): List<ParsedTrackPoint> = runCatching {
        val points = mutableListOf<ParsedTrackPoint>()
        fun readCoordinates(array: JSONArray, times: JSONArray?) {
            if (array.length() >= 2 && array.opt(0) is Number && array.opt(1) is Number) {
                val alt = if (array.length() > 2 && array.opt(2) is Number) array.optDouble(2) else null
                points += ParsedTrackPoint(array.optDouble(1), array.optDouble(0), alt)
                return
            }
            val flat = (0 until array.length()).all { array.opt(it) is JSONArray && (array.opt(it) as JSONArray).opt(0) is Number }
            for (i in 0 until array.length()) {
                val child = array.opt(i) as? JSONArray ?: continue
                val before = points.size
                readCoordinates(child, null)
                val time = if (flat && times != null && times.length() == array.length()) parseInstant(times.optString(i)) else null
                if (time != null && points.size == before + 1) points[before] = points[before].copy(timestamp = time)
            }
        }
        fun readObject(obj: JSONObject) {
            val times = obj.optJSONObject("properties")?.optJSONArray("coordTimes")
            obj.optJSONObject("geometry")?.optJSONArray("coordinates")?.let { readCoordinates(it, times) }
            obj.optJSONArray("coordinates")?.let { readCoordinates(it, times) }
            obj.optJSONArray("features")?.let { features ->
                for (i in 0 until features.length()) {
                    (features.opt(i) as? JSONObject)?.let { readObject(it) }
                }
            }
        }
        readObject(JSONObject(text))
        points
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ CSV

    private val LATITUDE = setOf("lat", "latitude", "decimallatitude", "decimal_latitude")
    private val LONGITUDE = setOf("lon", "lng", "long", "longitude", "decimallongitude", "decimal_longitude")
    private val ALTITUDE = setOf("alt", "altitude", "ele", "elevation", "elevacao")
    private val TIME = setOf("time", "timestamp", "datetime", "date_time", "data_hora", "datahora", "eventdate")

    private fun parseCsv(text: String): List<ParsedTrackPoint> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val separator = if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
        val header = lines.first().split(separator).map { it.trim().trim('"').lowercase(Locale.US) }
        val latIndex = header.indexOfFirst { it in LATITUDE }
        val lonIndex = header.indexOfFirst { it in LONGITUDE }
        if (latIndex >= 0 && lonIndex >= 0) {
            val altIndex = header.indexOfFirst { it in ALTITUDE }
            val timeIndex = header.indexOfFirst { it in TIME }
            return lines.drop(1).mapNotNull { line ->
                val cells = line.split(separator).map { it.trim().trim('"') }
                fun number(index: Int) = cells.getOrNull(index)?.replace(',', '.')?.toDoubleOrNull()
                val lat = number(latIndex)
                val lon = number(lonIndex)
                if (lat == null || lon == null) null
                else ParsedTrackPoint(lat, lon, if (altIndex >= 0) number(altIndex) else null, if (timeIndex >= 0) cells.getOrNull(timeIndex)?.let(::parseInstant) else null)
            }
        }
        val pairRegex = Regex("""(-?\d{1,2}\.\d+)\s*[,;]\s*(-?\d{1,3}\.\d+)""")
        return pairRegex.findAll(text).mapNotNull {
            val a = it.groupValues[1].toDoubleOrNull()
            val b = it.groupValues[2].toDoubleOrNull()
            if (a != null && b != null && a in -90.0..90.0 && b in -180.0..180.0) ParsedTrackPoint(a, b) else null
        }.toList()
    }

    /** Instante ISO 8601 com fuso ("2026-03-14T08:30:00Z" ou "-03:00"). Sem fuso nao vale: seria um palpite. */
    fun parseInstant(value: String): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
    }
}
