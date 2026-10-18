package com.oikos.fieldbook.services

import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/** Parametros de aquisicao de um arquivo de audio. */
data class AudioInfo(
    val sampleRateHz: Int?,
    val bitsPerSample: Int?,
    val channels: Int?,
    val durationMillis: Long?
) {
    /** "44,1 kHz / 16 bit / mono" para a tela e a exportacao. */
    fun summary(): String = listOfNotNull(
        sampleRateHz?.let { String.format(Locale.US, "%.1f kHz", it / 1000.0) },
        bitsPerSample?.let { "$it bit" },
        channels?.let { if (it == 1) "mono" else if (it == 2) "estéreo" else "$it canais" }
    ).joinToString(" / ")
}

/**
 * Le o cabecalho RIFF/WAVE. Sem dependencia de Android, para teste na JVM.
 * Arquivo truncado (gravacao interrompida) usa o tamanho real do arquivo no
 * lugar do tamanho declarado no cabecalho.
 */
object AudioInfoReader {

    fun readWav(file: File): AudioInfo? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(minOf(raf.length(), 64 * 1024L).toInt())
            raf.readFully(header)
            parseWav(header, raf.length())
        }
    }.getOrNull()

    fun parseWav(bytes: ByteArray, fileLength: Long): AudioInfo? {
        if (bytes.size < 12 || ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null
        var offset = 12
        var channels: Int? = null
        var sampleRate: Int? = null
        var byteRate: Int? = null
        var bits: Int? = null
        var dataBytes: Long? = null
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset)
            val size = le32(bytes, offset + 4)
            val body = offset + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                channels = le16(bytes, body + 2)
                sampleRate = le32(bytes, body + 4).toInt()
                byteRate = le32(bytes, body + 8).toInt()
                bits = le16(bytes, body + 14)
            } else if (id == "data") {
                val available = (fileLength - body).coerceAtLeast(0L)
                dataBytes = if (size == 0L || size > available) available else size
                break
            }
            offset = body + size.toInt() + (size % 2).toInt()
            if (size < 0 || offset <= body - 8) break
        }
        if (sampleRate == null) return null
        val duration = if (dataBytes != null && byteRate != null && byteRate > 0) dataBytes * 1000L / byteRate else null
        return AudioInfo(sampleRate, bits, channels, duration)
    }

    private fun ascii(bytes: ByteArray, offset: Int): String =
        if (offset + 4 <= bytes.size) String(bytes, offset, 4, Charsets.US_ASCII) else ""

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
}
