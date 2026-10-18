package com.oikos.fieldbook.audio

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Espectrograma pronto: [columns] x [bins], em dB, da frequencia 0 ate [maxFrequencyHz]. */
class SpectrogramResult(
    val columns: Int,
    val bins: Int,
    val sampleRate: Int,
    val durationSeconds: Double,
    val maxFrequencyHz: Double,
    /** Coluna a coluna: db[column * bins + bin]. */
    val db: FloatArray
) {
    /**
     * Pixels ARGB (largura = colunas, altura = faixas; grave embaixo).
     *
     * So na tela, o WAV nao muda: cada faixa de frequencia e mostrada acima do
     * seu proprio ruido de fundo. Antes a escala era fixa, 70 dB abaixo do pico
     * da gravacao, e com coro de insetos, riacho ou chuva o ruido ocupava a
     * imagem e o canto sumia (gravacao de Aplastodiscus arildae de 20/12/2025).
     * O fundo de cada faixa e o 20o percentil no tempo, e nao a mediana: com a
     * mediana, um coro presente em mais da metade da gravacao seria tomado por
     * ruido e apagado (conferido com um sinal de controle presente em 60% do
     * tempo). Um som continuo do inicio ao fim ainda some, como em qualquer
     * realce desse tipo. Gravacao muito curta usa a escala antiga.
     */
    fun pixels(palette: SpectrogramPalette = SpectrogramPalette.OIKOS): IntArray {
        val out = IntArray(columns * bins)
        if (columns < MIN_COLUMNS_FOR_FLOOR) {
            val top = db.maxOrNull() ?: 0f
            val floor = top - FIXED_RANGE_DB
            for (c in 0 until columns) {
                for (b in 0 until bins) {
                    val value = ((db[c * bins + b] - floor) / FIXED_RANGE_DB).coerceIn(0f, 1f)
                    out[(bins - 1 - b) * columns + c] = palette.color(value)
                }
            }
            return out
        }
        val band = FloatArray(columns)
        for (b in 0 until bins) {
            for (c in 0 until columns) band[c] = db[c * bins + b]
            band.sort()
            val floor = band[(columns * NOISE_PERCENTILE).toInt()] + FLOOR_OFFSET_DB
            for (c in 0 until columns) {
                val value = ((db[c * bins + b] - floor) / ENHANCED_RANGE_DB).coerceIn(0f, 1f)
                out[(bins - 1 - b) * columns + c] = palette.color(value)
            }
        }
        return out
    }

    companion object {
        const val NOISE_PERCENTILE = 0.2f
        /** Acima do 20o percentil: o ruido tambem oscila e nao deve acender a tela. */
        const val FLOOR_OFFSET_DB = 7f
        const val ENHANCED_RANGE_DB = 22f
        const val FIXED_RANGE_DB = 70f
        const val MIN_COLUMNS_FOR_FLOOR = 20
    }
}

/**
 * Espectrograma por transformada de Fourier de tempo curto (janela de Hann).
 * Recebe as amostras em fluxo ([push]): uma gravacao de 30 min nao precisa
 * caber na memoria.
 *
 * O audio inteiro e analisado, janela apos janela, e cada coluna mostra o
 * MAXIMO das janelas do seu trecho. Antes cada coluna era uma janela so de
 * 23 ms: numa gravacao de 5 min, so cerca de 7% do som entrava, e notas curtas
 * ou distorcao entre as janelas nunca apareciam.
 * Sem dependencia de Android, para teste na JVM.
 */
class SpectrogramBuilder(
    private val sampleRate: Int,
    private val totalFrames: Long,
    private val fftSize: Int = 1024,
    maxColumns: Int = 1000,
    maxFrequencyHz: Double = sampleRate / 2.0
) {
    init {
        require(fftSize > 0 && fftSize and (fftSize - 1) == 0) { "fftSize precisa ser potencia de 2" }
    }

    private val usableFrequency = min(maxFrequencyHz, sampleRate / 2.0)
    private val bins = max(1, (usableFrequency / sampleRate * fftSize).toInt())

    /**
     * Passo de meia janela (sobreposicao de 50%, o usual com Hann). Em
     * gravacao muito longa, passo de uma janela: continua cobrindo todo o
     * audio, com metade do calculo, para nao travar aparelho modesto.
     */
    private val hop: Int = if (totalFrames / (fftSize / 2) <= MAX_HALF_HOP_WINDOWS) fftSize / 2 else fftSize
    private val windows: Long = if (totalFrames < fftSize) 1 else (totalFrames - fftSize) / hop + 1
    val columns: Int = min(maxColumns.toLong(), windows).toInt().coerceAtLeast(1)
    private val window = DoubleArray(fftSize) { 0.5 - 0.5 * cos(2 * PI * it / (fftSize - 1)) }
    private val scale = 2.0 / window.sum()
    private val db = FloatArray(columns * bins) { -160f }
    private val filled = BooleanArray(columns)
    private val ring = DoubleArray(fftSize)
    private var position = 0L
    private var windowIndex = 0L
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)

    /** Uma amostra mono em -1..1. */
    fun push(sample: Float) {
        ring[(position % fftSize).toInt()] = sample.toDouble()
        position++
        if (position >= fftSize && windowIndex < windows && (position - fftSize) % hop == 0L) {
            val start = position - fftSize
            for (i in 0 until fftSize) re[i] = ring[((start + i) % fftSize).toInt()]
            analyze((windowIndex * columns / windows).toInt())
            windowIndex++
        }
    }

    fun result(): SpectrogramResult {
        // Gravacao mais curta que uma janela: o que houver, completado com zeros.
        if (windowIndex == 0L && position > 0L) {
            for (i in 0 until fftSize) re[i] = if (i < position) ring[i] else 0.0
            analyze(0)
            windowIndex++
        }
        return SpectrogramResult(columns, bins, sampleRate, totalFrames.toDouble() / sampleRate, bins.toDouble() * sampleRate / fftSize, db)
    }

    /** FFT da janela em [re] e maximo na coluna. */
    private fun analyze(column: Int) {
        for (i in 0 until fftSize) {
            re[i] *= window[i]
            im[i] = 0.0
        }
        Fft.transform(re, im)
        // Normaliza pela soma da janela: um seno de amplitude 1 da cerca de 0 dB.
        val base = column * bins
        val first = !filled[column]
        filled[column] = true
        for (b in 0 until bins) {
            val magnitude = sqrt(re[b] * re[b] + im[b] * im[b]) * scale
            val value = (20 * log10(magnitude + 1e-9)).toFloat()
            if (first || value > db[base + b]) db[base + b] = value
        }
    }

    private companion object {
        /** Ate ~12 min de audio a 44,1 kHz com sobreposicao de 50%. */
        const val MAX_HALF_HOP_WINDOWS = 60_000L
    }
}

/** FFT radix-2 no lugar (Cooley-Tukey). */
object Fft {
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2 * PI / length
            val wRe = cos(angle)
            val wIm = sin(angle)
            var start = 0
            while (start < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                start += length
            }
            length = length shl 1
        }
    }
}

/**
 * Escalas de cor do espectrograma. Nas duas a claridade sobe (ou desce) sem
 * voltar do silencio ao som mais forte, entao a leitura nao depende de
 * enxergar cores.
 */
enum class SpectrogramPalette(private val stops: IntArray) {
    /** Tema escuro: preto, marrom, o laranja do app, laranja claro e creme (L* de 5 a 96). */
    OIKOS(intArrayOf(0x101114, 0x3A1A08, 0xA43F00, 0xFF6A00, 0xFFB27A, 0xFFF1E3)),

    /** Tema para sol: fundo branco e som em preto, como nos espectrogramas publicados. */
    GRAY_INVERTED(intArrayOf(0xFFFFFF, 0x000000));

    fun color(value: Float): Int {
        val position = value.coerceIn(0f, 1f) * (stops.size - 1)
        val i = position.toInt().coerceAtMost(stops.size - 2)
        val t = position - i
        val a = stops[i]
        val b = stops[i + 1]
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

/**
 * Le um WAV PCM (8, 16, 24 ou 32 bits inteiros) em fluxo, misturando os canais
 * para mono. Devolve nulo se o arquivo nao for WAV PCM.
 */
object WavSpectrogram {
    fun compute(file: File, maxColumns: Int = 1000, maxFrequencyHz: Double = Double.MAX_VALUE): SpectrogramResult? {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(minOf(raf.length(), 64 * 1024L).toInt())
            raf.readFully(header)
            val format = findFormat(header) ?: return null
            val dataBytes = min(format.dataSize, raf.length() - format.dataOffset)
            val frameBytes = format.channels * format.bytesPerSample
            if (frameBytes <= 0) return null
            val totalFrames = dataBytes / frameBytes
            val builder = SpectrogramBuilder(format.sampleRate, totalFrames, maxColumns = maxColumns, maxFrequencyHz = maxFrequencyHz)
            raf.seek(format.dataOffset)
            val buffer = ByteArray(frameBytes * 4096)
            var remaining = totalFrames * frameBytes
            while (remaining > 0) {
                val read = raf.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                val frames = read / frameBytes
                for (f in 0 until frames) {
                    var sum = 0f
                    for (c in 0 until format.channels) sum += sample(buffer, f * frameBytes + c * format.bytesPerSample, format.bytesPerSample)
                    builder.push(sum / format.channels)
                }
                remaining -= read
            }
            return builder.result()
        }
    }

    private class Format(val sampleRate: Int, val channels: Int, val bytesPerSample: Int, val dataOffset: Long, val dataSize: Long)

    private fun findFormat(bytes: ByteArray): Format? {
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var offset = 12
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var audioFormat = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = le32(bytes, offset + 4)
            val body = offset + 8
            if (id == "fmt " && body + 16 <= bytes.size) {
                audioFormat = le16(bytes, body)
                channels = le16(bytes, body + 2)
                sampleRate = le32(bytes, body + 4).toInt()
                bits = le16(bytes, body + 14)
            } else if (id == "data") {
                // 1 = PCM; 0xFFFE = extensivel (PCM na pratica, nos gravadores comuns).
                if ((audioFormat != 1 && audioFormat != 0xFFFE) || sampleRate <= 0 || channels <= 0 || bits !in setOf(8, 16, 24, 32)) return null
                val declared = if (size == 0L) Long.MAX_VALUE else size
                return Format(sampleRate, channels, bits / 8, body.toLong(), declared)
            }
            // Pedaco maior que o cabecalho lido (ou tamanho corrompido, como
            // 0xFFFFFFF8): antes size.toInt() ficava negativo, o offset nao
            // andava e a leitura girava para sempre.
            val next = body.toLong() + size + (size % 2)
            if (next <= offset || next > bytes.size) return null
            offset = next.toInt()
        }
        return null
    }

    private fun sample(bytes: ByteArray, offset: Int, width: Int): Float = when (width) {
        1 -> ((bytes[offset].toInt() and 0xFF) - 128) / 128f
        2 -> ((bytes[offset].toInt() and 0xFF) or (bytes[offset + 1].toInt() shl 8)).toShort() / 32768f
        3 -> (((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or (bytes[offset + 2].toInt() shl 16)) / 8388608f)
        else -> ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or (bytes[offset + 3].toInt() shl 24)) / 2147483648f
    }

    private fun le16(bytes: ByteArray, offset: Int): Int = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or ((bytes[offset + 3].toLong() and 0xff) shl 24)
}
