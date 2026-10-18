package com.oikos.fieldbook.services

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class WavAudioRecorder {
    private val sampleRate = SAMPLE_RATE
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Uma gravacao, com sinal de parada, contador e thread proprios. Antes o
     * sinal era um so para todas: iniciar logo depois de parar religava a
     * thread anterior, que continuava gravando no arquivo velho.
     */
    private class Take(val thread: Thread, val running: AtomicBoolean, val bytes: AtomicLong)

    private val lock = Any()
    private var current: Take? = null
    @Volatile
    private var last: Take? = null

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

    /**
     * Falso quando a gravacao parou sozinha (microfone perdido, disco cheio,
     * limite do WAV) antes de alguem chamar [stop].
     */
    val isCapturing: Boolean get() = synchronized(lock) { current?.thread?.isAlive == true }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(file: File, preferredSources: List<Int> = listOf(MediaRecorder.AudioSource.MIC)) {
        stop()
        // A gravacao anterior precisa ter soltado o microfone e fechado o arquivo.
        last?.thread?.join(PREVIOUS_TIMEOUT_MILLIS)
        if (last?.thread?.isAlive == true) throw IllegalStateException("A gravação anterior ainda está sendo fechada. Tente de novo em instantes.")
        file.parentFile?.mkdirs()
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = minBuffer.coerceAtLeast(sampleRate)
        // Tenta as fontes na ordem pedida; a primeira que o aparelho inicializa vence.
        var audioRecord: AudioRecord? = null
        var chosenSource = ""
        for (source in preferredSources + MediaRecorder.AudioSource.MIC) {
            val candidate = runCatching { AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize) }.getOrNull()
            if (candidate?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = candidate
                chosenSource = sourceLabel(source)
                break
            }
            candidate?.release()
        }
        val recorder: AudioRecord = audioRecord
            ?: throw IllegalStateException("Microfone indisponível: outro app pode estar gravando.")
        // Iniciado aqui para a falha aparecer na hora: durante uma ligacao o
        // Android aceita o pedido mas nao grava, e antes a tela mostrava
        // "gravando" sobre um arquivo vazio.
        val began = runCatching { recorder.startRecording() }.isSuccess && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
        if (!began) {
            recorder.release()
            throw IllegalStateException("O microfone está ocupado (ligação em curso ou outro app gravando).")
        }
        sourceName = chosenSource
        lastError = null
        val running = AtomicBoolean(true)
        val bytes = AtomicLong(0L)
        // A thread e dona do AudioRecord: so ela le e so ela libera.
        val worker = thread(start = false, name = "oikos-wav-recorder") {
            try {
                RandomAccessFile(file, "rw").use { output ->
                    output.setLength(0)
                    writeHeader(output, 0)
                    val buffer = ByteArray(bufferSize)
                    var nextHeaderUpdate = HEADER_UPDATE_BYTES
                    try {
                        while (running.get()) {
                            val read = recorder.read(buffer, 0, buffer.size)
                            // Erro do microfone: antes o laco girava sem gravar
                            // nada e a tela seguia mostrando "gravando".
                            if (read < 0) throw IOException("o microfone parou de responder (código $read)")
                            if (read == 0) continue
                            val count = minOf(read.toLong(), MAX_DATA_BYTES - bytes.get()).toInt() and 1.inv()
                            output.write(buffer, 0, count)
                            val total = bytes.addAndGet(count.toLong())
                            level = peakLevel(buffer, count)
                            if (total >= MAX_DATA_BYTES) throw IOException("atingido o limite de 4 GB do formato WAV (cerca de 13 horas)")
                            // Cabecalho atualizado a cada segundo: se o app
                            // morrer no meio, o WAV continua legivel ate ali.
                            if (total >= nextHeaderUpdate) {
                                writeHeader(output, total)
                                output.seek(44 + total)
                                nextHeaderUpdate = total + HEADER_UPDATE_BYTES
                                // Para antes de encher o disco: sem espaco, nem o
                                // registro do audio no banco seria gravado.
                                if ((file.parentFile ?: file).usableSpace < MIN_FREE_BYTES) {
                                    throw IOException("armazenamento quase cheio; a gravação foi encerrada para o áudio poder ser registrado")
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        lastError = error
                    }
                    runCatching { recorder.stop() }
                    // Cabecalho final com o que foi gravado, mesmo depois de uma
                    // falha: o trecho ate ali continua utilizavel.
                    runCatching { writeHeader(output, bytes.get()) }.onFailure { if (lastError == null) lastError = it }
                }
            } catch (error: Throwable) {
                if (lastError == null) lastError = error
            } finally {
                recorder.release()
                level = 0f
            }
        }
        val take = Take(worker, running, bytes)
        synchronized(lock) { current = take }
        last = take
        worker.start()
    }

    /** Para a gravacao e espera o cabecalho WAV ser fechado. Chamar fora da main thread. */
    fun stop() {
        val take = synchronized(lock) { current.also { current = null } } ?: return
        take.running.set(false)
        take.thread.join(STOP_TIMEOUT_MILLIS)
    }

    /** Duracao da gravacao atual (ou da ultima), em milissegundos. */
    fun durationMillis(): Long = (last?.bytes?.get() ?: 0L) * 1000L / (sampleRate * 2L)

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
        // Campos de 32 bits sem sinal: os 32 bits de baixo valem ate 4 GB.
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
        /** Maior trecho de dados que o campo de tamanho do RIFF comporta, em amostras inteiras. */
        private const val MAX_DATA_BYTES = 0xFFFFFFFFL - 36L - 1L
        /** Espaco minimo que precisa sobrar para o banco registrar o audio. */
        const val MIN_FREE_BYTES = 10L * 1024 * 1024
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val PREVIOUS_TIMEOUT_MILLIS = 1_500L
    }
}
