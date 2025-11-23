package com.thivyanstudios.hark.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.thivyanstudios.hark.MainActivity
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.pow

class AudioStreamingService : Service() {

    private val binder = LocalBinder()
    private var streamingExecutor: ExecutorService? = null
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var disableHearingAidPriority = false
    private var microphoneGain = 1.0f

    private val _hearingAidConnected = MutableStateFlow(false)
    val hearingAidConnected = _hearingAidConnected.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateHearingAidStatus()
            if (_isStreaming.value) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    restartStreaming()
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.S)
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateHearingAidStatus()
            if (_isStreaming.value) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    restartStreaming()
                }
            }
            val deviceType = if(disableHearingAidPriority) AudioDeviceInfo.TYPE_BLUETOOTH_SCO else AudioDeviceInfo.TYPE_HEARING_AID
            if (removedDevices?.any { it.type == deviceType } == true) {
                stopStreaming()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamingService = this@AudioStreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        userPreferencesRepository = UserPreferencesRepository(applicationContext)

        userPreferencesRepository.disableHearingAidPriority
            .onEach { newValue ->
                val hasChanged = newValue != disableHearingAidPriority
                disableHearingAidPriority = newValue
                if (hasChanged && _isStreaming.value) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        stopStreaming()
                    }
                }
                updateHearingAidStatus()
            }
            .launchIn(serviceScope)

        userPreferencesRepository.microphoneGain
            .onEach { microphoneGain = 10.0.pow(it / 20.0).toFloat() }
            .launchIn(serviceScope)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hark::AudioStreamingWakeLock")
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    private fun updateHearingAidStatus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val deviceType = if(disableHearingAidPriority) AudioDeviceInfo.TYPE_BLUETOOTH_SCO else AudioDeviceInfo.TYPE_HEARING_AID
            _hearingAidConnected.value = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type == deviceType }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    private fun restartStreaming() {
        if (_isStreaming.value) {
            Log.d(TAG, "Restarting streaming due to device change.")
            stopStreaming()
            startStreaming()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    @SuppressLint("ForegroundServiceType")
    fun startStreaming() {
        if (_isStreaming.value) {
            Log.d(TAG, "startStreaming called, but already streaming.")
            return
        }
        Log.d(TAG, "startStreaming called.")
        _isStreaming.value = true

        startForeground(NOTIFICATION_ID, createNotification())
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minute timeout, for safety

        val executor = Executors.newCachedThreadPool()
        this.streamingExecutor = executor

        executor.execute {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

            val audioPlayBack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()

            var audioRecord: AudioRecord? = null

            try {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
                audioRecord.startRecording()

                audioPlayBack.play()

                while (_isStreaming.value) {
                    val buffer = FloatArray(bufferSize / 4)
                    val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        for (i in 0 until read) {
                            buffer[i] *= microphoneGain
                        }
                        audioPlayBack.write(buffer, 0, read, AudioTrack.WRITE_BLOCKING)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in streaming loop", e)
            } finally {
                Log.d(TAG, "Cleaning up streaming resources.")
                audioRecord?.stop()
                audioRecord?.release()
                audioPlayBack.stop()
                audioPlayBack.release()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun stopStreaming() {
        if (!_isStreaming.value) return
        Log.d(TAG, "stopStreaming called.")
        _isStreaming.value = false

        streamingExecutor?.shutdownNow()
        try {
            if (streamingExecutor?.awaitTermination(1, TimeUnit.SECONDS) == false) {
                Log.e(TAG, "Streaming executor did not terminate in time.")
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for executor termination", e)
        }

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
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
            .setSmallIcon(R.drawable.ic_mic_on)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val TAG = "AudioStreamingService"
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopStreaming()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopStreaming()
        stopSelf()
    }
}
