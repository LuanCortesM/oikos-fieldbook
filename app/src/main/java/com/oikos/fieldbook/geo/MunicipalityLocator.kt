package com.oikos.fieldbook.geo

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class Municipality(val code: Int, val name: String, val uf: String) {
    val label: String get() = "$name – $uf"
}

/** Onde o ponto cai e o que merece conferencia. */
data class PlaceCheck(
    /** Municipio que contem o ponto, ou nulo fora da malha (mar, outro pais). */
    val municipality: Municipality?,
    /** Distancia ate a divisa do proprio municipio. */
    val borderDistanceMeters: Double?,
    /** Municipio vizinho mais proximo, quando o ponto esta perto da divisa ou fora da malha. */
    val nearest: Municipality?,
    val nearestDistanceMeters: Double?
)

/**
 * Municipio e UF de uma coordenada, sem internet, pela Malha Municipal 2022 do
 * IBGE (arquivo gerado por tools/ibge/MalhaMunicipal.java). Busca por caixa
 * envolvente e ponto-em-poligono; os aneis so sao decodificados para os
 * municipios cuja caixa contem o ponto. Sem dependencia de Android.
 */
class MunicipalityLocator(private val data: ByteArray) {

    private class Entry(val municipality: Municipality, val bbox: IntArray, val offset: Int, val length: Int)

    private val scale: Int
    private val entries: List<Entry>

    /** Poucos municipios decodificados por vez bastam: os pontos de campo se repetem na mesma regiao. */
    private val cache = object : LinkedHashMap<Int, List<IntArray>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<IntArray>>?): Boolean = size > 32
    }

    init {
        val input = DataInputStream(ByteArrayInputStream(data))
        val magic = ByteArray(4).also { input.readFully(it) }
        if (String(magic, Charsets.US_ASCII) != "OIKM") throw IOException("malha municipal com formato desconhecido")
        if (input.readByte().toInt() != 1) throw IOException("versão da malha municipal não suportada")
        scale = input.readInt()
        val count = input.readInt()
        entries = List(count) {
            val code = input.readInt()
            val name = input.readUTF()
            val uf = input.readUTF()
            val bbox = IntArray(4) { input.readInt() }
            val length = readVarint(input)
            val offset = data.size - input.available()
            input.skipBytes(length)
            Entry(Municipality(code, name, uf), bbox, offset, length)
        }
    }

    val size: Int get() = entries.size

    fun byCode(code: Int): Municipality? = entries.firstOrNull { it.municipality.code == code }?.municipality

    fun locate(latitude: Double, longitude: Double): Municipality? = find(toUnits(longitude), toUnits(latitude))?.municipality

    /**
     * Municipio do ponto e, se ele estiver a menos de [nearMeters] da divisa (ou
     * fora da malha a menos de [searchMeters]), o municipio mais proximo.
     */
    @Synchronized
    fun check(latitude: Double, longitude: Double, nearMeters: Double = 250.0, searchMeters: Double = 20_000.0): PlaceCheck {
        val lon = toUnits(longitude)
        val lat = toUnits(latitude)
        val inside = find(lon, lat)
        val border = inside?.let { distanceToBoundary(it, lon, lat) }
        val limit = if (inside != null) nearMeters else searchMeters
        if (inside != null && border != null && border > nearMeters) return PlaceCheck(inside.municipality, border, null, null)
        val margin = metersToUnits(limit, latitude)
        var nearest: Entry? = null
        var nearestDistance = Double.MAX_VALUE
        for (entry in entries) {
            if (entry === inside) continue
            val b = entry.bbox
            if (lon < b[0] - margin || lon > b[2] + margin || lat < b[1] - margin || lat > b[3] + margin) continue
            val d = distanceToBoundary(entry, lon, lat)
            if (d < nearestDistance) {
                nearest = entry
                nearestDistance = d
            }
        }
        return if (nearest != null && nearestDistance <= limit) {
            PlaceCheck(inside?.municipality, border, nearest.municipality, nearestDistance)
        } else {
            PlaceCheck(inside?.municipality, border, null, null)
        }
    }

    private fun find(lon: Int, lat: Int): Entry? = synchronized(this) {
        entries.firstOrNull { entry ->
            val b = entry.bbox
            lon >= b[0] && lon <= b[2] && lat >= b[1] && lat <= b[3] && contains(rings(entry), lon, lat)
        }
    }

    private fun rings(entry: Entry): List<IntArray> = cache.getOrPut(entry.municipality.code) {
        val input = DataInputStream(ByteArrayInputStream(data, entry.offset, entry.length))
        List(readVarint(input)) {
            val n = readVarint(input)
            val ring = IntArray(n * 2)
            var lon = 0
            var lat = 0
            for (i in 0 until n) {
                lon += unzigzag(readVarint(input))
                lat += unzigzag(readVarint(input))
                ring[2 * i] = lon
                ring[2 * i + 1] = lat
            }
            ring
        }
    }

    /** Par-impar sobre todos os aneis: buracos e ilhas do mesmo municipio funcionam. */
    private fun contains(rings: List<IntArray>, lon: Int, lat: Int): Boolean {
        var inside = false
        for (ring in rings) {
            val n = ring.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[2 * i]
                val yi = ring[2 * i + 1]
                val xj = ring[2 * j]
                val yj = ring[2 * j + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi).toDouble() * (lat - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** Metros, em aproximacao plana local: suficiente para distancias de ate dezenas de km. */
    private fun distanceToBoundary(entry: Entry, lon: Int, lat: Int): Double {
        val kx = cos(Math.toRadians(lat.toDouble() / scale)) * METERS_PER_DEGREE_LON / scale
        val ky = METERS_PER_DEGREE_LAT / scale
        var best = Double.MAX_VALUE
        for (ring in rings(entry)) {
            val n = ring.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val ax = (ring[2 * j] - lon) * kx
                val ay = (ring[2 * j + 1] - lat) * ky
                val bx = (ring[2 * i] - lon) * kx
                val by = (ring[2 * i + 1] - lat) * ky
                val dx = bx - ax
                val dy = by - ay
                val length = dx * dx + dy * dy
                val t = if (length == 0.0) 0.0 else (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0)
                val px = ax + t * dx
                val py = ay + t * dy
                best = minOf(best, sqrt(px * px + py * py))
                j = i
            }
        }
        return best
    }

    private fun toUnits(degrees: Double): Int = Math.round(degrees * scale).toInt()

    private fun metersToUnits(meters: Double, latitude: Double): Int {
        val perDegree = METERS_PER_DEGREE_LON * maxOf(0.2, abs(cos(Math.toRadians(latitude))))
        return (meters / perDegree * scale).toInt() + 1
    }

    companion object {
        private const val METERS_PER_DEGREE_LON = 111_320.0
        private const val METERS_PER_DEGREE_LAT = 110_574.0

        const val SOURCE = "IBGE, Malha Municipal 2022"

        private fun unzigzag(v: Int): Int = (v ushr 1) xor -(v and 1)

        private fun readVarint(input: DataInputStream): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = input.readUnsignedByte()
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
    }
}
