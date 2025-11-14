package com.tapps.hark

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess


class AudioStreamingService : Service() {

    private val binder = LocalBinder()
    private var streamingThread: Thread? = null
    private val isStreaming = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any { it.type == AudioDeviceInfo.TYPE_HEARING_AID }) {
                // Gracefully stop streaming to release the WakeLock and other resources.
                stopStreaming()

                // Show a toast and then forcefully terminate the app after a delay.
                mainHandler.post {
                    Toast.makeText(applicationContext, "Please reconnect your hearing systems.", Toast.LENGTH_SHORT).show()
                    val notificationManager =
                        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_ID)
                    mainHandler.postDelayed({ exitProcess(0) }, 0) // 0-second delay
                }
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamingService = this@AudioStreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hark::AudioStreamingWakeLock")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @SuppressLint("ForegroundServiceType")
    fun startStreaming() {
        if (isStreaming.getAndSet(true)) return

        startForeground(NOTIFICATION_ID, createNotification())
        wakeLock?.acquire(10*60*1000L) // 10 minute timeout, for safety

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        streamingThread = Thread {
            var audioRecord: AudioRecord? = null
            var audioPlayBack: AudioTrack? = null
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val sampleRate = 44100
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufferSize)
                audioPlayBack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val buffer = ByteArray(minBufferSize)
                audioRecord.startRecording()
                audioPlayBack.play()
                broadcastStreamingState(true)

                while (isStreaming.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        audioPlayBack.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioStreamingService", "Error in streaming thread", e)
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioPlayBack?.stop()
                audioPlayBack?.release()

                broadcastStreamingState(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }.apply { start() }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun stopStreaming() {
        if (!isStreaming.getAndSet(false)) return

        // 1. Tell the system this is no longer a foreground service.
        //    This will remove the notification.
        stopForeground(STOP_FOREGROUND_REMOVE)

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            Log.w("AudioStreamingService", "Audio device callback already unregistered")
        }

        streamingThread?.interrupt()
        streamingThread = null
    }

    fun isStreaming(): Boolean = isStreaming.get()
    
    private fun broadcastStreamingState(isStreaming: Boolean) {
        val intent = Intent("com.tapps.hark.STREAMING_STATE_CHANGED").putExtra("isStreaming", isStreaming)
        sendBroadcast(intent)
    }

    private fun createNotification(): Notification {
        val channelId = "hark_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "HARK Audio Streaming"
            val descriptionText = "Notification for ongoing audio streaming"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("HARK is running")
            .setContentText("Streaming live audio")
            .setSmallIcon(R.drawable.white_mic_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setWhen(System.currentTimeMillis()) // Explicitly set the start time
            .setUsesChronometer(true) // Enable the timer
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopStreaming()
        stopSelf()
    }
}