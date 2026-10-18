package com.oikos.fieldbook.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Parametros de aquisicao lidos do cabecalho WAV acompanham cada gravacao exportada. */
class AudioInfoReaderTest {

    private fun wav(sampleRate: Int, bits: Int, channels: Int, dataBytes: Int, declaredData: Int = dataBytes): ByteArray {
        val out = ByteArrayOutputStream()
        fun int(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff); out.write(v shr 16 and 0xff); out.write(v shr 24 and 0xff) }
        fun short(v: Int) { out.write(v and 0xff); out.write(v shr 8 and 0xff) }
        out.write("RIFF".toByteArray()); int(36 + declaredData); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(channels); int(sampleRate)
        int(sampleRate * channels * bits / 8); short(channels * bits / 8); short(bits)
        out.write("data".toByteArray()); int(declaredData)
        out.write(ByteArray(dataBytes))
        return out.toByteArray()
    }

    @Test
    fun `le taxa bits canais e duracao`() {
        // 2 s de mono 16 bit a 44,1 kHz = 176400 bytes
        val bytes = wav(44_100, 16, 1, 176_400)
        val info = AudioInfoReader.parseWav(bytes, bytes.size.toLong())!!
        assertEquals(44_100, info.sampleRateHz)
        assertEquals(16, info.bitsPerSample)
        assertEquals(1, info.channels)
        assertEquals(2_000L, info.durationMillis)
        assertEquals("44.1 kHz / 16 bit / mono", info.summary())
    }

    @Test
    fun `gravacao interrompida usa o tamanho real do arquivo`() {
        // Cabecalho diz 0 bytes de dados (app morreu antes de fechar), arquivo tem 1 s.
        val bytes = wav(44_100, 16, 1, 88_200, declaredData = 0)
        val info = AudioInfoReader.parseWav(bytes, bytes.size.toLong())!!
        assertEquals(1_000L, info.durationMillis)
    }

    @Test
    fun `estereo 48 kHz 24 bit`() {
        val bytes = wav(48_000, 24, 2, 48_000 * 2 * 3)
        val info = AudioInfoReader.parseWav(bytes, bytes.size.toLong())!!
        assertEquals(48_000, info.sampleRateHz)
        assertEquals(24, info.bitsPerSample)
        assertEquals(2, info.channels)
        assertEquals(1_000L, info.durationMillis)
    }

    @Test
    fun `arquivo que nao e WAV devolve nulo`() {
        assertNull(AudioInfoReader.parseWav("ID3 isto e mp3".toByteArray(), 14))
        assertNull(AudioInfoReader.parseWav(ByteArray(4), 4))
    }
}
