package com.oikos.fieldbook.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaKindTest {
    private fun kind(vararg bytes: Int) = MediaKind.detect(ByteArray(bytes.size) { bytes[it].toByte() })
    private fun kind(text: String) = MediaKind.detect(text.toByteArray(Charsets.ISO_8859_1))

    @Test
    fun reconheceAsAssinaturasMaisComuns() {
        assertEquals("jpg", kind(0xFF, 0xD8, 0xFF, 0xE1)?.extension)
        assertEquals("png", kind(0x89, 'P'.code, 'N'.code, 'G'.code)?.extension)
        assertEquals("wav", kind("RIFF\u0000\u0000\u0000\u0000WAVEfmt ")?.extension)
        assertEquals("webp", kind("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ")?.extension)
        assertEquals("m4a", kind("\u0000\u0000\u0000 ftypM4A \u0000\u0000")?.extension)
        assertEquals("heic", kind("\u0000\u0000\u0000\u0018ftypheic\u0000\u0000")?.extension)
        assertEquals("ogg", kind("OggS\u0000\u0002")?.extension)
        assertEquals("flac", kind("fLaC\u0000")?.extension)
        assertEquals("mp3", kind("ID3\u0004\u0000")?.extension)
        assertEquals("mp3", kind(0xFF, 0xFB, 0x90, 0x00)?.extension)
        assertEquals("aac", kind(0xFF, 0xF1, 0x50, 0x80)?.extension)
        assertEquals("amr", kind("#!AMR\n")?.extension)
    }

    @Test
    fun conteudoDesconhecidoNaoInventaTipo() {
        assertNull(kind("texto qualquer"))
        assertNull(kind())
    }
}
