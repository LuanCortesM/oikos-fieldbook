package com.oikos.fieldbook.services

import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Data de gravacao de um audio importado de gravador externo. Nao existe um
 * padrao unico entre gravadores; a busca segue esta ordem:
 * 1. BWF, chunk "bext" (EBU Tech 3285): OriginationDate (10 caracteres) e
 *    OriginationTime (8), nos bytes 320 e 330 do chunk, o mesmo leiaute do
 *    leitor do FFmpeg;
 * 2. GUANO, chunk "guan" em UTF-8: campo Timestamp em ISO 8601, com o fuso
 *    quando informado (detectores de morcego, Song Meter);
 * 3. comentario ICMT do AudioMoth, que o firmware escreve como
 *    "Recorded at 21:00:00 03/04/2017 (UTC+1)" (hora local e fuso);
 * 4. data no titulo INAM ou no nome original do arquivo: AAAAMMDD_HHMMSS
 *    (AudioMoth, Song Meter) ou AAMMDD_HHMM (gravadores de voz Sony, como o
 *    ICD-PX470, que nao guarda a data em outro lugar).
 * Sem fuso declarado, vale a hora local do celular que importou. A data de
 * modificacao do arquivo nao entra: ela muda quando o arquivo e copiado.
 * Sem dependencia de Android, para teste na JVM.
 */
object AudioRecordingDate {

    /** Textos do WAV que podem trazer a data; o audio em si e pulado sem ler. */
    data class WavText(val bext: ByteArray? = null, val guano: String? = null, val info: Map<String, String> = emptyMap())

    private const val MAX_TEXT_CHUNK = 1L shl 20
    private val wanted = setOf("bext", "guan", "LIST")

    fun find(file: File, originalName: String?, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): Long? =
        find(runCatching { readText(file) }.getOrNull() ?: WavText(), originalName, zone, now)

    fun find(text: WavText, originalName: String?, zone: ZoneId, now: Long): Long? =
        sequenceOf<() -> Long?>(
            { text.bext?.let { fromBext(it, zone) } },
            { text.guano?.let { fromGuano(it, zone) } },
            { text.info["ICMT"]?.let { fromAudioMoth(it) } },
            { text.info["INAM"]?.let { fromName(it, zone) } },
            { originalName?.let { fromName(it, zone) } }
        ).mapNotNull { it() }.firstOrNull { plausible(it, now) }

    fun readText(file: File): WavText = RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        if (length < 12) return WavText()
        val head = ByteArray(12)
        raf.readFully(head)
        if (ascii(head, 0, 4) != "RIFF" || ascii(head, 8, 4) != "WAVE") return WavText()
        var bext: ByteArray? = null
        var guano: String? = null
        val info = linkedMapOf<String, String>()
        val header = ByteArray(8)
        var position = 12L
        var chunks = 0
        while (position + 8 <= length && chunks++ < 256) {
            raf.seek(position)
            raf.readFully(header)
            val id = ascii(header, 0, 4)
            val size = le32(header, 4)
            val body = position + 8
            // Tamanho maior que o arquivo: gravacao interrompida; nada vem depois.
            if (size > length - body) break
            if (id in wanted && size <= MAX_TEXT_CHUNK) {
                val bytes = ByteArray(size.toInt())
                raf.readFully(bytes)
                when (id) {
                    "bext" -> bext = bytes
                    "guan" -> guano = String(bytes, Charsets.UTF_8)
                    "LIST" -> if (ascii(bytes, 0, 4) == "INFO") info.putAll(infoFields(bytes))
                }
            }
            position = body + size + (size % 2)
        }
        WavText(bext, guano, info)
    }

    /** OriginationDate "aaaa-mm-dd" e OriginationTime "hh:mm:ss"; o separador pode variar. */
    fun fromBext(bytes: ByteArray, zone: ZoneId): Long? {
        if (bytes.size < 338) return null
        val date = ascii(bytes, 320, 10).filter { it.isDigit() }
        val time = ascii(bytes, 330, 8).filter { it.isDigit() }
        if (date.length != 8 || time.length != 6) return null
        return local(date.substring(0, 4), date.substring(4, 6), date.substring(6, 8), time.substring(0, 2), time.substring(2, 4), time.substring(4, 6))
            ?.atZone(zone)?.toInstant()?.toEpochMilli()
    }

    /** Campo Timestamp do GUANO; sem fuso no valor, vale a hora local. */
    fun fromGuano(text: String, zone: ZoneId): Long? {
        val value = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.substringBefore(':').trim() == "Timestamp" }
            ?.substringAfter(':')?.trim()
            ?: return null
        val iso = (if (value.length > 10 && value[10] == ' ') value.substring(0, 10) + "T" + value.substring(11) else value)
            .replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        runCatching { return OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
        return runCatching { LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    private val audioMoth = Regex("Recorded at (\\d{2}):(\\d{2}):(\\d{2}) (\\d{2})/(\\d{2})/(\\d{4}) \\(UTC(?:([+-])(\\d{1,2}))?(?::(\\d{2}))?\\)")

    /** Hora local e fuso do comentario do AudioMoth (dia/mes/ano). */
    fun fromAudioMoth(comment: String): Long? {
        val m = audioMoth.find(comment) ?: return null
        val (hh, mi, ss, dd, mo, yyyy) = m.destructured
        val sign = if (m.groupValues[7] == "-") -1 else 1
        val hours = m.groupValues[8].toIntOrNull() ?: 0
        val minutes = m.groupValues[9].toIntOrNull() ?: 0
        val offset = runCatching { ZoneOffset.ofHoursMinutes(sign * hours, sign * minutes) }.getOrNull() ?: return null
        return local(yyyy, mo, dd, hh, mi, ss)?.toInstant(offset)?.toEpochMilli()
    }

    private val longStamp = Regex("(?<!\\d)(\\d{4})(\\d{2})(\\d{2})[_T-]?(\\d{2})(\\d{2})(\\d{2})(?!\\d)")
    private val sonyStamp = Regex("(?<!\\d)(\\d{2})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(?!\\d)")

    /** AAAAMMDD_HHMMSS (AudioMoth, Song Meter) ou AAMMDD_HHMM (Sony), em hora local. */
    fun fromName(name: String, zone: ZoneId): Long? {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        longStamp.find(base)?.destructured?.let { (y, mo, d, h, mi, s) ->
            local(y, mo, d, h, mi, s)?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        sonyStamp.find(base)?.destructured?.let { (yy, mo, d, h, mi) ->
            local("20$yy", mo, d, h, mi, "00")?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        return null
    }

    private fun local(y: String, mo: String, d: String, h: String, mi: String, s: String): LocalDateTime? =
        runCatching { LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt()) }.getOrNull()

    /** Relogio zerado (1970, 1904) ou data no futuro nao valem. */
    private fun plausible(millis: Long, now: Long): Boolean =
        millis >= 631_152_000_000L && millis <= now + 86_400_000L

    /** Subcampos do LIST/INFO (INAM, ICMT, IART...), sem os zeros do fim. */
    private fun infoFields(bytes: ByteArray): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        var offset = 4
        while (offset + 8 <= bytes.size) {
            val id = ascii(bytes, offset, 4)
            val size = le32(bytes, offset + 4).toInt()
            val body = offset + 8
            if (size < 0 || body + size > bytes.size) break
            fields[id] = String(bytes, body, size, Charsets.UTF_8).trimEnd('\u0000').trim()
            offset = body + size + (size % 2)
        }
        return fields
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        if (offset + length <= bytes.size) String(bytes, offset, length, Charsets.ISO_8859_1) else ""

    private fun le32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
}
