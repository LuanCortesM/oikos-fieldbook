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
    fun start(file: File) {
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
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            pcmFormat,
            bufferSize
        )
        audioRecord = recorder
        codec = encoder
        isRecording.set(true)
        worker = thread(name = "oikos-mp3-recorder") {
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
                        if (read > 0 && input != null) {
                            input.put(pcm, 0, read)
                            encoder.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0)
                            presentationTimeUs += read / 2L * 1_000_000L / sampleRate
                        }
                    }
                    drainEncoder(encoder, info, output)
                }
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                var done = false
                while (!done) {
                    done = drainEncoder(encoder, info, output)
                }
                runCatching { recorder.stop() }
                runCatching { encoder.stop() }
            }
            recorder.release()
            encoder.release()
        }
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return
        worker?.join(1500)
        runCatching { audioRecord?.release() }
        runCatching { codec?.release() }
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
