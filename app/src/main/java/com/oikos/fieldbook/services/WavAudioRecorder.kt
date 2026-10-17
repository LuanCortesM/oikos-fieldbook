package com.oikos.fieldbook.services

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class WavAudioRecorder {
    private val sampleRate = SAMPLE_RATE
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val isRecording = AtomicBoolean(false)
    private val bytesWritten = AtomicLong(0L)
    private var worker: Thread? = null

    @Volatile
    var lastError: Throwable? = null
        private set

    /** Nivel do ultimo bloco lido, 0..1, para o medidor da tela. */
    @Volatile
    var level: Float = 0f
        private set

    /** Fonte de audio efetivamente usada na ultima gravacao (nome legivel). */
    @Volatile
    var sourceName: String = ""
        private set

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(file: File, preferredSources: List<Int> = listOf(MediaRecorder.AudioSource.MIC)) {
        stop()
        file.parentFile?.mkdirs()
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBuffer.coerceAtLeast(sampleRate)
        // Tenta as fontes na ordem pedida; a primeira que o aparelho inicializa vence.
        var audioRecord: AudioRecord? = null
        for (source in preferredSources + MediaRecorder.AudioSource.MIC) {
            val candidate = runCatching { AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize) }.getOrNull()
            if (candidate?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = candidate
                sourceName = sourceLabel(source)
                break
            }
            candidate?.release()
        }
        val recorder: AudioRecord = audioRecord
            ?: throw IllegalStateException("Microfone indisponível: outro app pode estar gravando.")
        lastError = null
        bytesWritten.set(0L)
        isRecording.set(true)
        // A thread e dona do AudioRecord: so ela le e so ela libera. Antes o
        // stop() liberava o microfone de fora enquanto a leitura ainda corria.
        worker = thread(name = "oikos-wav-recorder") {
            try {
                RandomAccessFile(file, "rw").use { output ->
                    output.setLength(0)
                    writeHeader(output, 0)
                    val buffer = ByteArray(bufferSize)
                    var nextHeaderUpdate = HEADER_UPDATE_BYTES
                    recorder.startRecording()
                    while (isRecording.get()) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            val total = bytesWritten.addAndGet(read.toLong())
                            level = peakLevel(buffer, read)
                            // Cabecalho atualizado a cada segundo: se o app
                            // morrer no meio, o WAV continua legivel ate ali.
                            if (total >= nextHeaderUpdate) {
                                writeHeader(output, total)
                                output.seek(44 + total)
                                nextHeaderUpdate = total + HEADER_UPDATE_BYTES
                            }
                        }
                    }
                    runCatching { recorder.stop() }
                    writeHeader(output, bytesWritten.get())
                }
            } catch (error: Throwable) {
                lastError = error
            } finally {
                recorder.release()
                level = 0f
            }
        }
    }

    /** Para a gravacao e espera o cabecalho WAV ser fechado. Chamar fora da main thread. */
    fun stop() {
        if (!isRecording.getAndSet(false)) return
        worker?.join(5_000)
        worker = null
    }

    /** Duracao gravada ate agora, em milissegundos. */
    fun durationMillis(): Long = bytesWritten.get() * 1000L / (sampleRate * 2L)

    private fun peakLevel(buffer: ByteArray, length: Int): Float {
        var peak = 0
        var index = 0
        while (index + 1 < length) {
            val sample = (buffer[index].toInt() and 0xff) or (buffer[index + 1].toInt() shl 8)
            val magnitude = kotlin.math.abs(sample.toShort().toInt())
            if (magnitude > peak) peak = magnitude
            index += 2
        }
        return (peak / 32768f).coerceIn(0f, 1f)
    }

    private fun writeHeader(file: RandomAccessFile, dataSize: Long) {
        file.seek(0)
        val byteRate = sampleRate * 2
        file.writeBytes("RIFF")
        file.writeIntLE((36 + dataSize).toInt())
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        file.writeIntLE(16)
        file.writeShortLE(1)
        file.writeShortLE(1)
        file.writeIntLE(sampleRate)
        file.writeIntLE(byteRate)
        file.writeShortLE(2)
        file.writeShortLE(16)
        file.writeBytes("data")
        file.writeIntLE(dataSize.toInt())
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
        write(value shr 16 and 0xff)
        write(value shr 24 and 0xff)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
    }

    companion object {
        fun sourceLabel(source: Int): String = when (source) {
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.MIC -> "MIC"
            else -> source.toString()
        }

        const val SAMPLE_RATE = 44_100
        private const val HEADER_UPDATE_BYTES = SAMPLE_RATE * 2L
    }
}
