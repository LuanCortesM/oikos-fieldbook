package com.oikos.fieldbook.services

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class PureMp3AudioRecorder {
    private val sampleRate = 44_100
    private val bitRate = 128_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val pcmFormat = AudioFormat.ENCODING_PCM_16BIT
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(file: File, preferredSources: List<Int> = listOf(MediaRecorder.AudioSource.MIC)) {
        stop()
        file.parentFile?.mkdirs()
        val encoder = runCatching { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG) }
            .getOrElse { throw IllegalStateException("Este aparelho não possui encoder MP3 puro (audio/mpeg). Use WAV puro ou instale um build com LAME/FFmpeg.", it) }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_MPEG, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
        }
        runCatching { encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
            .getOrElse {
                encoder.release()
                throw IllegalStateException("Encoder MP3 puro indisponível neste aparelho. Use WAV puro ou um build com LAME/FFmpeg.", it)
            }
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, pcmFormat)
        val bufferSize = minBuffer.coerceAtLeast(sampleRate)
        // Mesma ordem de fontes do WAV: sem processamento quando possivel.
        var chosen: AudioRecord? = null
        for (source in preferredSources + MediaRecorder.AudioSource.MIC) {
            val candidate = runCatching { AudioRecord(source, sampleRate, channelConfig, pcmFormat, bufferSize) }.getOrNull()
            if (candidate?.state == AudioRecord.STATE_INITIALIZED) {
                chosen = candidate
                break
            }
            candidate?.release()
        }
        val recorder: AudioRecord = chosen ?: run {
            encoder.release()
            throw IllegalStateException("Microfone indisponível: outro app pode estar gravando.")
        }
        audioRecord = recorder
        codec = encoder
        lastError = null
        recordedMicros = 0L
        isRecording.set(true)
        worker = thread(name = "oikos-mp3-recorder") {
            try {
                FileOutputStream(file).use { output ->
                    val info = MediaCodec.BufferInfo()
                    val pcm = ByteArray(bufferSize)
                    var presentationTimeUs = 0L
                    encoder.start()
                    recorder.startRecording()
                    while (isRecording.get()) {
                        val inputIndex = encoder.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val input = encoder.getInputBuffer(inputIndex)
                            input?.clear()
                            val read = recorder.read(pcm, 0, pcm.size)
                            // Buffer retirado do encoder sempre volta, mesmo vazio.
                            val size = if (read > 0 && input != null) read else 0
                            if (size > 0) input?.put(pcm, 0, size)
                            encoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
                            presentationTimeUs += size / 2L * 1_000_000L / sampleRate
                            recordedMicros = presentationTimeUs
                        }
                        drainEncoder(encoder, info, output)
                    }
                    runCatching { recorder.stop() }
                    // Fim de fluxo com prazo: se o encoder nao confirmar em 3 s,
                    // fecha assim mesmo em vez de girar para sempre.
                    val deadline = System.currentTimeMillis() + 3_000L
                    var eosQueued = false
                    var done = false
                    while (!done && System.currentTimeMillis() < deadline) {
                        if (!eosQueued) {
                            val inputIndex = encoder.dequeueInputBuffer(10_000)
                            if (inputIndex >= 0) {
                                encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                eosQueued = true
                            }
                        }
                        done = drainEncoder(encoder, info, output)
                    }
                    runCatching { encoder.stop() }
                }
            } catch (error: Throwable) {
                lastError = error
            } finally {
                runCatching { recorder.release() }
                runCatching { encoder.release() }
            }
        }
    }

    @Volatile
    var lastError: Throwable? = null
        private set

    @Volatile
    private var recordedMicros: Long = 0L

    fun durationMillis(): Long = recordedMicros / 1000L

    /**
     * Para a gravacao. A thread de gravacao e quem libera microfone e encoder;
     * liberar daqui, com a leitura ainda em curso, podia derrubar o app.
     * Chamar fora da main thread.
     */
    fun stop() {
        if (!isRecording.getAndSet(false)) return
        worker?.join(5_000)
        audioRecord = null
        codec = null
        worker = null
    }

    private fun drainEncoder(codec: MediaCodec, info: MediaCodec.BufferInfo, output: FileOutputStream): Boolean {
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(info, 0)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outputIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(bytes)
                        output.write(bytes)
                    }
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (end) return true
                }
            }
        }
    }
}
