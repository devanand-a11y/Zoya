package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.audio.AudioPlayerManager
import com.example.data.audio.AudioRecorderManager
import com.example.data.remote.GeminiLiveSessionManager
import com.example.data.remote.ZoyaState
import com.example.data.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZoyaVoiceService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    lateinit var toolEngine: ToolExecutionEngine
        private set
    lateinit var audioPlayer: AudioPlayerManager
        private set
    lateinit var audioRecorder: AudioRecorderManager
        private set
    lateinit var liveSession: GeminiLiveSessionManager
        private set

    val sessionState: StateFlow<ZoyaState> get() = liveSession.sessionState
    val statusMessage: StateFlow<String> get() = liveSession.statusMessage
    val lastToolExecuted: StateFlow<String?> get() = liveSession.lastToolExecuted
    val recorderAmplitude: StateFlow<Float> get() = audioRecorder.amplitude
    val playerAmplitude: StateFlow<Float> get() = audioPlayer.outputAmplitude

    companion object {
        private const val CHANNEL_ID = "zoya_voice_assistant_channel"
        private const val NOTIF_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }

    inner class LocalBinder : Binder() {
        fun getService(): ZoyaVoiceService = this@ZoyaVoiceService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        toolEngine = ToolExecutionEngine(applicationContext)
        audioPlayer = AudioPlayerManager()
        audioPlayer.initialize()

        audioRecorder = AudioRecorderManager(applicationContext)
        liveSession = GeminiLiveSessionManager(applicationContext, toolEngine, audioPlayer)

        startForeground(NOTIF_ID, buildNotification("Zoya active. Wake-word 'Zoya' ready."))
        _isServiceRunning.value = true

        startAudioLoop()
    }

    private fun startAudioLoop() {
        liveSession.startLiveSession()

        audioRecorder.startRecording(
            scope = serviceScope,
            onAudioChunk = { pcmChunk ->
                liveSession.sendAudioChunk(pcmChunk)
            },
            onWakeWordDetected = {
                serviceScope.launch {
                    // Instantly trigger/awaken live session if needed
                    if (liveSession.sessionState.value == ZoyaState.IDLE || liveSession.sessionState.value == ZoyaState.ERROR) {
                        liveSession.startLiveSession()
                    }
                }
            },
            onUserSpeechDetected = {
                // Interruption handling: User started speaking while Zoya was outputting audio
                if (audioPlayer.isSpeaking.value) {
                    audioPlayer.stopPlayback()
                }
            }
        )
    }

    fun restartSession() {
        liveSession.closeSession()
        liveSession.startLiveSession()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zoya Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Zoya voice assistant listening for wake-word in background"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zoya AI Assistant")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {

        audioRecorder.stopRecording()
        audioPlayer.release()
        liveSession.closeSession()
        serviceScope.cancel()
        _isServiceRunning.value = false
        super.onDestroy()
    }
}
