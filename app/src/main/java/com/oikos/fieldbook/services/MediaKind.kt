package com.oikos.fieldbook.services

import java.io.File

/** Arquivo importado e o tipo MIME do seu conteudo. */
data class ImportedFile(val file: File, val mimeType: String)

/**
 * Tipo de midia pela assinatura dos primeiros bytes. O tipo que o outro app
 * informa costuma ser generico ou errado (octet-stream, ou audio generico para M4A).
 */
data class MediaKind(val mime: String, val extension: String) {
    companion object {
        fun detect(file: File): MediaKind? = runCatching {
            val head = ByteArray(16)
            val read = file.inputStream().use { it.read(head) }
            detect(head.copyOf(read.coerceAtLeast(0)))
        }.getOrNull()

        fun detect(head: ByteArray): MediaKind? {
            fun ascii(offset: Int, length: Int): String =
                if (head.size >= offset + length) String(head, offset, length, Charsets.ISO_8859_1) else ""
            fun byte(i: Int): Int = if (i < head.size) head[i].toInt() and 0xff else -1
            return when {
                byte(0) == 0xFF && byte(1) == 0xD8 && byte(2) == 0xFF -> MediaKind("image/jpeg", "jpg")
                byte(0) == 0x89 && ascii(1, 3) == "PNG" -> MediaKind("image/png", "png")
                ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> MediaKind("image/webp", "webp")
                ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" -> MediaKind("audio/wav", "wav")
                ascii(4, 4) == "ftyp" && ascii(8, 4) in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1") -> MediaKind("image/heic", "heic")
                ascii(4, 4) == "ftyp" && ascii(8, 3) == "M4A" -> MediaKind("audio/mp4", "m4a")
                ascii(4, 4) == "ftyp" && ascii(8, 4) in setOf("isom", "mp42", "mp41", "3gp4", "3gp5", "3gp6") -> MediaKind("audio/mp4", "m4a")
                ascii(0, 4) == "OggS" -> MediaKind("audio/ogg", "ogg")
                ascii(0, 4) == "fLaC" -> MediaKind("audio/flac", "flac")
                ascii(0, 5) == "#!AMR" -> MediaKind("audio/amr", "amr")
                ascii(0, 3) == "ID3" -> MediaKind("audio/mpeg", "mp3")
                // Quadro MPEG sem ID3: 11 bits de sincronismo, camada III.
                byte(0) == 0xFF && (byte(1) and 0xE0) == 0xE0 && (byte(1) and 0x06) == 0x02 -> MediaKind("audio/mpeg", "mp3")
                // ADTS (AAC cru): 12 bits de sincronismo, camada 00.
                byte(0) == 0xFF && (byte(1) and 0xF6) == 0xF0 -> MediaKind("audio/aac", "aac")
                else -> null
            }
        }
    }
}
