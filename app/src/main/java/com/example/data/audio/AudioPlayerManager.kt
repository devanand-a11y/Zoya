package com.example.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

class AudioPlayerManager {

    private val sampleRate = 24000 // Gemini Live output rate is typically 24kHz or 16kHz PCM
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile private var isPlaying = false

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude: StateFlow<Float> = _outputAmplitude.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    @Synchronized
    fun initialize() {
        if (audioTrack != null) return

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    fun playChunk(pcmChunk: ByteArray) {
        if (audioTrack == null) {
            initialize()
        }
        audioQueue.add(pcmChunk)
        if (!isPlaying) {
            startPlaybackLoop()
        }
    }

    private fun startPlaybackLoop() {
        isPlaying = true
        _isSpeaking.value = true

        Thread {
            while (isPlaying) {
                val chunk = audioQueue.poll()
                if (chunk != null && chunk.isNotEmpty()) {
                    val rms = calculateRms(chunk)
                    val normAmp = (rms / 32768f).coerceIn(0f, 1f)
                    _outputAmplitude.value = normAmp

                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (audioQueue.isEmpty()) {
                        _outputAmplitude.value = 0f
                        _isSpeaking.value = false
                        isPlaying = false
                        break
                    }
                }
            }
        }.start()
    }

    /**
     * Instantly interrupts Zoya's speaking output when user speaks or triggers interruption
     */
    fun stopPlayback() {
        audioQueue.clear()
        isPlaying = false
        _isSpeaking.value = false
        _outputAmplitude.value = 0f
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        stopPlayback()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioTrack = null
        }
    }

    private fun calculateRms(pcmData: ByteArray): Float {
        var sum = 0.0
        var i = 0
        while (i < pcmData.size - 1) {
            val sample = (pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += shortSample * shortSample
            i += 2
        }
        val count = pcmData.size / 2
        return if (count > 0) sqrt(sum / count).toFloat() else 0f
    }
}
