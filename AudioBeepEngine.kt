package com.example.airaligner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin

class AudioBeepEngine {
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var currentDbm: Int = -90

    fun start() {
        if (isRunning) return
        isRunning = true

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioTrack?.play()

        CoroutineScope(Dispatchers.Default).launch {
            audioLoop()
        }
    }

    fun updateSignal(dbm: Int) {
        currentDbm = dbm.coerceIn(-95, -40)
    }

    private suspend fun audioLoop() {
        var sampleIndex = 0
        while (isRunning) {
            // Масштабируем dBm (-90...-45) в частоту звука (400 Гц...1600 Гц)
            val normalized = ((currentDbm + 90).toFloat() / 45f).coerceIn(0f, 1f)
            val frequency = 400.0 + (normalized * 1200.0)
            
            // Длительность пакета сэмплов (около 50 мс)
            val chunkSize = sampleRate / 20
            val buffer = ShortArray(chunkSize)

            for (i in 0 until chunkSize) {
                val angle = 2.0 * Math.PI * sampleIndex * frequency / sampleRate
                buffer[i] = (sin(angle) * Short.MAX_VALUE * 0.5).toInt().toShort()
                sampleIndex++
            }

            audioTrack?.write(buffer, 0, buffer.size)

            // Пауза между писками: при хорошем сигнале пищит чаще
            val delayMs = ((1f - normalized) * 300).toLong() + 50
            delay(delayMs)
        }
    }

    fun stop() {
        isRunning = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
