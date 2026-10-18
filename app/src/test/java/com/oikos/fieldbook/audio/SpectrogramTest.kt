package com.oikos.fieldbook.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SpectrogramTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** WAV PCM 16 bits com um tom de [frequency] Hz, estereo ou mono. */
    private fun toneWav(frequency: Double, seconds: Double, sampleRate: Int = 44_100, channels: Int = 2): File {
        val frames = (seconds * sampleRate).toInt()
        val data = ByteArrayOutputStream()
        for (i in 0 until frames) {
            val value = (sin(2 * PI * frequency * i / sampleRate) * 0.5 * 32767).toInt()
            repeat(channels) {
                data.write(value and 0xFF)
                data.write((value shr 8) and 0xFF)
            }
        }
        val body = data.toByteArray()
        fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        val file = File(temp.root, "tom-${System.nanoTime()}.wav")
        file.outputStream().use { out ->
            out.write("RIFF".toByteArray()); out.write(le32(36 + body.size)); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); out.write(le32(16)); out.write(le16(1)); out.write(le16(channels))
            out.write(le32(sampleRate)); out.write(le32(sampleRate * channels * 2)); out.write(le16(channels * 2)); out.write(le16(16))
            out.write("data".toByteArray()); out.write(le32(body.size)); out.write(body)
        }
        return file
    }

    private fun peakBin(result: SpectrogramResult, column: Int): Int =
        (0 until result.bins).maxByOrNull { result.db[column * result.bins + it] }!!

    @Test
    fun tomDe3kHzApareceNaFaixaCerta() {
        val result = WavSpectrogram.compute(toneWav(3000.0, 2.0))!!
        val expected = 3000.0 / (result.maxFrequencyHz / result.bins)
        assertEquals(expected, peakBin(result, result.columns / 2).toDouble(), 1.0)
        assertEquals(2.0, result.durationSeconds, 0.01)
        // Amplitude 0,5 -> cerca de -6 dB.
        assertEquals(-6.0, result.db.maxOrNull()!!.toDouble(), 1.5)
    }

    @Test
    fun limiteDeFrequenciaCortaAsFaixas() {
        val full = WavSpectrogram.compute(toneWav(1000.0, 1.0, channels = 1))!!
        val limited = WavSpectrogram.compute(toneWav(1000.0, 1.0, channels = 1), maxFrequencyHz = 6000.0)!!
        assertTrue(limited.bins < full.bins)
        assertTrue(limited.maxFrequencyHz in 5900.0..6000.0)
        assertEquals(peakBin(full, full.columns / 2), peakBin(limited, limited.columns / 2))
    }

    @Test
    fun gravacaoLongaRespeitaOMaximoDeColunas() {
        val result = WavSpectrogram.compute(toneWav(500.0, 30.0, sampleRate = 8_000, channels = 1), maxColumns = 200)!!
        assertEquals(200, result.columns)
        assertTrue("toda coluna recebeu janela", result.db.none { it == -160f })
    }

    @Test
    fun arquivoQueNaoEWavDevolveNulo() {
        val file = temp.newFile("nao.wav").apply { writeText("isto nao e audio") }
        assertNull(WavSpectrogram.compute(file))
    }

    @Test
    fun fftDeUmImpulsoEPlana() {
        val re = DoubleArray(8).also { it[0] = 1.0 }
        val im = DoubleArray(8)
        Fft.transform(re, im)
        assertTrue(re.all { abs(it - 1.0) < 1e-12 } && im.all { abs(it) < 1e-12 })
    }

    @Test
    fun paletasVaoDoSilencioAoSomForte() {
        assertEquals(0xFF101114.toInt(), SpectrogramPalette.OIKOS.color(0f))
        assertEquals(0xFFFFF1E3.toInt(), SpectrogramPalette.OIKOS.color(1f))
        assertEquals(0xFFFFFFFF.toInt(), SpectrogramPalette.GRAY_INVERTED.color(0f))
        assertEquals(0xFF000000.toInt(), SpectrogramPalette.GRAY_INVERTED.color(1f))
    }

    /** Na escala Oikos a claridade so sobe: a leitura nao depende de distinguir cores. */
    @Test
    fun escalaOikosSempreClareia() {
        fun luminance(argb: Int): Double {
            fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
            return 0.2126 * lin(argb shr 16 and 255) + 0.7152 * lin(argb shr 8 and 255) + 0.0722 * lin(argb and 255)
        }
        var previous = -1.0
        for (i in 0..100) {
            val l = luminance(SpectrogramPalette.OIKOS.color(i / 100f))
            assertTrue("claridade caiu em $i%", l >= previous)
            previous = l
        }
    }

    /** Canto presente em 60% do tempo nao pode ser tomado por ruido de fundo e apagado. */
    @Test
    fun coroFrequenteNaoSomeNoRealce() {
        val columns = 100
        val bins = 4
        val db = FloatArray(columns * bins) { -60f }
        for (c in 0 until columns) if (c % 10 < 6) db[c * bins + 2] = -20f
        val result = SpectrogramResult(columns, bins, 8000, 10.0, 2000.0, db)
        val pixels = result.pixels(SpectrogramPalette.GRAY_INVERTED)
        // Faixa 2 (linha bins - 1 - 2): coluna com canto escura, sem canto branca.
        val row = bins - 1 - 2
        assertEquals(0xFF000000.toInt(), pixels[row * columns + 0])
        assertEquals(0xFFFFFFFF.toInt(), pixels[row * columns + 7])
    }

    @Test
    fun estaloCurtoEntreJanelasAparece() {
        // 5 min a 8 kHz com um estalo de 2 ms no meio: com uma janela por
        // coluna, o estalo caia entre as janelas e sumia.
        val sampleRate = 8_000
        val frames = 5 * 60 * sampleRate
        val builder = SpectrogramBuilder(sampleRate, frames.toLong(), maxColumns = 300)
        val clickAt = frames / 2 + 137
        for (i in 0 until frames) builder.push(if (i in clickAt until clickAt + 16) 0.9f else 0f)
        val result = builder.result()
        val loudest = result.db.maxOrNull()!!
        assertTrue("estalo visivel: $loudest dB", loudest > -40f)
    }

    @Test
    fun gravacaoMaisCurtaQueUmaJanelaNaoFicaVazia() {
        val builder = SpectrogramBuilder(8_000, 300)
        for (i in 0 until 300) builder.push((sin(2 * PI * 1000.0 * i / 8_000) * 0.5).toFloat())
        assertTrue(builder.result().db.any { it > -60f })
    }

    @Test
    fun wavComTamanhoDeBlocoCorrompidoNaoTrava() {
        // Bloco "LIST" com tamanho 0xFFFFFFF8: antes o laco nunca terminava.
        fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        val file = File(temp.root, "corrompido.wav")
        file.outputStream().use { out ->
            out.write("RIFF".toByteArray()); out.write(le32(100)); out.write("WAVE".toByteArray())
            out.write("LIST".toByteArray()); out.write(le32(0xFFFFFFF8L)); out.write(ByteArray(64))
        }
        assertNull(WavSpectrogram.compute(file))
    }
}
