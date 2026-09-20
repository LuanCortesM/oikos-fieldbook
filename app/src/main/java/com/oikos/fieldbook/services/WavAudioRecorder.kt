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
import kotlin.concurrent.thread

class WavAudioRecorder {
    private val sampleRate = 44_100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val isRecording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(file: File) {
        stop()
        file.parentFile?.mkdirs()
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBuffer.coerceAtLeast(sampleRate)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        recorder = audioRecord
        isRecording.set(true)
        worker = thread(name = "oikos-wav-recorder") {
            RandomAccessFile(file, "rw").use { output ->
                output.setLength(0)
                writeHeader(output, 0)
                val buffer = ByteArray(bufferSize)
                var dataSize = 0L
                audioRecord.startRecording()
                while (isRecording.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                        dataSize += read
                    }
                }
                runCatching { audioRecord.stop() }
                writeHeader(output, dataSize)
            }
            audioRecord.release()
        }
    }

    fun stop() {
        if (!isRecording.getAndSet(false)) return
        worker?.join(1200)
        recorder?.release()
        recorder = null
        worker = null
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
}
