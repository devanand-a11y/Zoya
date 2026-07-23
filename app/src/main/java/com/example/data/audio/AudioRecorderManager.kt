package com.example.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioRecorderManager(private val context: Context) {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAudioChunk: (ByteArray) -> Unit,
        onWakeWordDetected: () -> Unit,
        onUserSpeechDetected: () -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, 2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            _isListening.value = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(1024)
                var silentFramesCount = 0
                var activeSpeechCount = 0

                while (isRecording && audioRecord != null) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readBytes > 0) {
                        val chunk = buffer.copyOf(readBytes)
                        
                        // Calculate RMS amplitude
                        val rms = calculateRms(chunk)
                        val normalizedAmp = (rms / 32768f).coerceIn(0f, 1f)
                        _amplitude.value = normalizedAmp

                        // Check voice activity
                        if (normalizedAmp > 0.08f) {
                            activeSpeechCount++
                            if (activeSpeechCount >= 2) {
                                onUserSpeechDetected() // trigger interruption
                            }

                            // Simple energy-based wake word pattern check
                            if (normalizedAmp > 0.25f && activeSpeechCount % 5 == 0) {
                                onWakeWordDetected()
                            }
                        } else {
                            silentFramesCount++
                            if (silentFramesCount > 10) {
                                activeSpeechCount = 0
                            }
                        }

                        // Send PCM chunk to session
                        onAudioChunk(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            _isListening.value = false
        }
    }

    fun stopRecording() {
        isRecording = false
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
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
