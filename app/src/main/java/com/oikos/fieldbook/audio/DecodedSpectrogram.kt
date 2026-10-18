package com.oikos.fieldbook.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Espectrograma de audio comprimido (MP3, M4A, OGG...) decodificado pelo
 * MediaCodec do aparelho, em fluxo. WAV vai direto por [WavSpectrogram].
 */
object DecodedSpectrogram {

    fun compute(file: File, maxColumns: Int = 1000, maxFrequencyHz: Double = Double.MAX_VALUE): SpectrogramResult? {
        WavSpectrogram.compute(file, maxColumns, maxFrequencyHz)?.let { return it }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (!format.containsKey(MediaFormat.KEY_DURATION)) return null
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            // Taxa, canais e codificacao valem os da SAIDA do decodificador: no
            // HE-AAC a saida tem o dobro da taxa de entrada (SBR), e alguns
            // aparelhos entregam PCM em ponto flutuante. Antes valia a entrada:
            // frequencias saiam pela metade, ou o float era lido como inteiro.
            var floatPcm = false
            var builder: SpectrogramBuilder? = null
            fun ensureBuilder(): SpectrogramBuilder = builder ?: SpectrogramBuilder(
                sampleRate, durationUs * sampleRate / 1_000_000L, maxColumns = maxColumns, maxFrequencyHz = maxFrequencyHz
            ).also { builder = it }
            fun readOutputFormat(output: MediaFormat) {
                if (builder == null && output.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (output.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                floatPcm = output.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    output.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
            }
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val buffer = codec.getInputBuffer(inputIndex) ?: continue
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                    if (outputIndex >= 0) {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            if (builder == null) runCatching { readOutputFormat(codec.getOutputFormat(outputIndex)) }
                            val target = ensureBuilder()
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            val ordered = output.order(ByteOrder.LITTLE_ENDIAN)
                            if (floatPcm) {
                                val samples = ordered.asFloatBuffer()
                                val count = samples.remaining()
                                var i = 0
                                while (i + channels <= count) {
                                    var sum = 0f
                                    for (c in 0 until channels) sum += samples.get(i + c)
                                    target.push(sum / channels)
                                    i += channels
                                }
                            } else {
                                // Saida padrao do decodificador: PCM 16 bits intercalado.
                                val samples = ordered.asShortBuffer()
                                val count = samples.remaining()
                                var i = 0
                                while (i + channels <= count) {
                                    var sum = 0f
                                    for (c in 0 until channels) sum += samples.get(i + c) / 32768f
                                    target.push(sum / channels)
                                    i += channels
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        readOutputFormat(codec.outputFormat)
                    }
                }
                codec.stop()
            } finally {
                codec.release()
            }
            return builder?.result()
        } finally {
            extractor.release()
        }
    }
}
