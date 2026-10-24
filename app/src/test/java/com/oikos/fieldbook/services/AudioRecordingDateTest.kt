package com.oikos.fieldbook.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

class AudioRecordingDateTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val brasilia = ZoneId.of("America/Sao_Paulo")
    private val now = OffsetDateTime.parse("2026-10-24T12:00:00-03:00").toInstant().toEpochMilli()

    private fun local(text: String) = LocalDateTime.parse(text).atZone(brasilia).toInstant().toEpochMilli()

    private fun le32(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())

    private fun chunk(id: String, body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(id.toByteArray(Charsets.US_ASCII)); write(le32(body.size)); write(body)
        if (body.size % 2 == 1) write(0)
    }.toByteArray()

    private fun info(vararg fields: Pair<String, String>): ByteArray = chunk("LIST", ByteArrayOutputStream().apply {
        write("INFO".toByteArray())
        fields.forEach { (id, value) -> write(chunk(id, (value + "\u0000").toByteArray())) }
    }.toByteArray())

    private fun bext(date: String, time: String): ByteArray = chunk("bext", ByteArray(602).also { body ->
        date.toByteArray().copyInto(body, 320)
        time.toByteArray().copyInto(body, 330)
    })

    private fun wav(name: String, vararg chunks: ByteArray): File {
        val fmt = chunk("fmt ", ByteArray(16).also { it[0] = 1; it[2] = 1 })
        val body = ByteArrayOutputStream().apply {
            write("WAVE".toByteArray()); write(fmt); chunks.forEach { write(it) }
        }.toByteArray()
        return folder.newFile(name).apply { writeBytes("RIFF".toByteArray() + le32(body.size) + body) }
    }

    private val data = chunk("data", ByteArray(400))

    @Test
    fun gravadorSonyGuardaADataSoNoTituloENoNome() {
        val file = wav("AUDIO_import.wav", info("INAM" to "251220_1847", "IART" to "My Recording"), data)
        assertEquals(local("2025-12-20T18:47:00"), AudioRecordingDate.find(file, null, brasilia, now))
    }

    @Test
    fun nomeOriginalDoArquivoQuandoNaoHaNadaDentro() {
        val file = wav("x.wav", data)
        assertEquals(local("2025-12-20T18:47:00"), AudioRecordingDate.find(file, "251220_1847.WAV", brasilia, now))
        assertEquals(local("2025-12-20T21:30:05"), AudioRecordingDate.find(file, "S4A12345_20251220_213005.wav", brasilia, now))
    }

    @Test
    fun bextTemPrioridadeSobreONome() {
        val file = wav("b.wav", bext("2026:10:01", "06-30-15"), data)
        assertEquals(local("2026-10-01T06:30:15"), AudioRecordingDate.find(file, "251220_1847.wav", brasilia, now))
    }

    @Test
    fun guanoComFusoUsaOFusoDoArquivo() {
        val file = wav("g.wav", chunk("guan", "GUANO|Version:  1.0\nTimestamp:  2012-03-29T03:58:01+04:00\nMake:  Pettersson\n".toByteArray()), data)
        assertEquals(OffsetDateTime.parse("2012-03-29T03:58:01+04:00").toInstant().toEpochMilli(), AudioRecordingDate.find(file, null, brasilia, now))
    }

    @Test
    fun guanoSemFusoVaiParaAHoraLocal() {
        val file = wav("g2.wav", chunk("guan", "GUANO|Version: 1.0\nTimestamp: 2025-11-02 20:15:00\n".toByteArray()), data)
        assertEquals(local("2025-11-02T20:15:00"), AudioRecordingDate.find(file, null, brasilia, now))
    }

    @Test
    fun comentarioDoAudioMoth() {
        assertEquals(
            OffsetDateTime.parse("2017-04-03T21:00:00+01:00").toInstant().toEpochMilli(),
            AudioRecordingDate.fromAudioMoth("Recorded at 21:00:00 03/04/2017 (UTC+1) by AudioMoth 248B5B055B4D9C3A at medium gain")
        )
        assertEquals(
            OffsetDateTime.parse("2017-04-03T21:00:00Z").toInstant().toEpochMilli(),
            AudioRecordingDate.fromAudioMoth("Recorded at 21:00:00 03/04/2017 (UTC) by AudioMoth 248B5B055B4D9C3A")
        )
        assertEquals(
            OffsetDateTime.parse("2017-04-03T21:00:00-03:30").toInstant().toEpochMilli(),
            AudioRecordingDate.fromAudioMoth("Recorded at 21:00:00 03/04/2017 (UTC-3:30) during deployment 0A1B2C3D4E5F6071 ")
        )
        val file = wav("am.wav", info("ICMT" to "Recorded at 05:10:00 15/09/2026 (UTC-3) by AudioMoth 248B5B055B4D9C3A", "IART" to "AudioMoth 248B5B055B4D9C3A"), data)
        assertEquals(local("2026-09-15T05:10:00"), AudioRecordingDate.find(file, null, brasilia, now))
    }

    @Test
    fun listDepoisDoAudioTambemELido() {
        val file = wav("depois.wav", data, info("INAM" to "260101_0510"))
        assertEquals(local("2026-01-01T05:10:00"), AudioRecordingDate.find(file, null, brasilia, now))
    }

    @Test
    fun relogioZeradoDataFuturaEArquivoSemDataNaoValem() {
        assertNull(AudioRecordingDate.find(wav("zero.wav", bext("0000-00-00", "00:00:00"), data), "19700101_000000.wav", brasilia, now))
        assertNull(AudioRecordingDate.find(wav("futuro.wav", data), "20991231_235959.wav", brasilia, now))
        assertNull(AudioRecordingDate.find(wav("nada.wav", data), "Gravacao 12.wav", brasilia, now))
    }

    @Test
    fun arquivoQueNaoEWavAindaUsaONome() {
        val m4a = folder.newFile("rec.m4a").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        assertEquals(local("2025-12-20T18:47:00"), AudioRecordingDate.find(m4a, "251220_1847.m4a", brasilia, now))
    }
}
