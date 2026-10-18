package com.oikos.fieldbook.geo

import android.content.Context
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.floor

/**
 * Ondulacao do geoide EGM2008 (grade de 15', gerada por
 * tools/geoide/GeoideEgm2008.java) para converter a altitude do GPS em
 * altitude sobre o nivel medio do mar.
 *
 * O GPS do Android informa a altura sobre o elipsoide WGS84 (h). A altitude
 * de mapas, cartas e altimetros e sobre o geoide (H). H = h - N, onde N e a
 * ondulacao do geoide no ponto (no Rio de Janeiro, cerca de -5,8 m; em Manaus,
 * -11,8 m). Antes o app exportava h como dwc:minimumElevationInMeters.
 *
 * O mesmo calculo em todos os aparelhos: o conversor do proprio Android so
 * existe a partir do Android 14.
 */
class Geoid(input: InputStream) {
    private val stepDegrees: Double
    private val cols: Int
    private val rows: Int
    private val values: ShortArray

    init {
        val data = DataInputStream(input.buffered())
        val magic = ByteArray(4).also { data.readFully(it) }
        if (String(magic, Charsets.US_ASCII) != "OIKG" || data.readByte().toInt() != 1) throw IOException("arquivo do geoide invalido")
        stepDegrees = data.readShort() / 60.0
        cols = data.readUnsignedShort()
        rows = data.readUnsignedShort()
        values = ShortArray(cols * rows)
        val bytes = ByteArray(values.size * 2).also { data.readFully(it) }
        for (i in values.indices) values[i] = ((bytes[2 * i].toInt() shl 8) or (bytes[2 * i + 1].toInt() and 0xFF)).toShort()
    }

    /** Ondulacao N em metros (interpolacao bilinear). */
    fun undulation(latitude: Double, longitude: Double): Double {
        val y = (90.0 - latitude.coerceIn(-90.0, 90.0)) / stepDegrees
        val x = ((longitude % 360.0) + 360.0) % 360.0 / stepDegrees
        val r0 = floor(y).toInt().coerceIn(0, rows - 2)
        val c0 = floor(x).toInt() % cols
        val c1 = (c0 + 1) % cols
        val fy = (y - r0).coerceIn(0.0, 1.0)
        val fx = x - floor(x)
        val a = values[r0 * cols + c0].toDouble()
        val b = values[r0 * cols + c1].toDouble()
        val c = values[(r0 + 1) * cols + c0].toDouble()
        val d = values[(r0 + 1) * cols + c1].toDouble()
        return ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy) / 100.0
    }

    /** Altitude sobre o nivel medio do mar a partir da altura elipsoidal do GPS. */
    fun aboveSeaLevel(ellipsoidalHeight: Double, latitude: Double, longitude: Double): Double =
        ellipsoidalHeight - undulation(latitude, longitude)

    companion object {
        /** Valor de dwc:verticalDatum para altitudes convertidas por esta classe. */
        const val VERTICAL_DATUM = "EGM2008"
        private const val ASSET = "geoide_egm2008.bin"

        @Volatile
        private var shared: Geoid? = null

        /** Carregado uma vez por processo (2 MB). Nulo se o arquivo faltar. */
        fun get(context: Context): Geoid? {
            shared?.let { return it }
            return synchronized(this) {
                shared ?: runCatching { context.applicationContext.assets.open(ASSET).use { Geoid(it) } }.getOrNull()?.also { shared = it }
            }
        }
    }
}
