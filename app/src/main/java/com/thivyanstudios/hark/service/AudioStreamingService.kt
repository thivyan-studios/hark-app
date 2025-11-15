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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AudioStreamingService : Service() {

    private val binder = LocalBinder()
    private var streamingExecutor: ExecutorService? = null
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()
    private var wakeLock: PowerManager.WakeLock? = null

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
            if (removedDevices?.any { it.type == AudioDeviceInfo.TYPE_HEARING_AID } == true) {
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
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hark::AudioStreamingWakeLock")
        updateHearingAidStatus()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    private fun updateHearingAidStatus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            _hearingAidConnected.value = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type == AudioDeviceInfo.TYPE_HEARING_AID }
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
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val microphones = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { isMicrophone(it) }

            if (microphones.isEmpty()) {
                Log.e(TAG, "No microphones found!")
                return@execute
            }

            Log.d(TAG, "Found ${microphones.size} microphones.")

            val audioRecords = microphones.mapNotNull { mic -> createAudioRecordFor(mic) }
            if (audioRecords.isEmpty()) {
                Log.e(TAG, "Could not create any AudioRecord instances.")
                return@execute
            }

            val bufferSize = minBufferSize / 2
            val audioBuffers = audioRecords.map { ShortArray(bufferSize) }
            val mixedBuffer = ShortArray(bufferSize)

            val audioPlayBackBuilder = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioPlayBackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val audioPlayBack = audioPlayBackBuilder.build()

            try {
                audioRecords.forEach { it.startRecording() }
                audioPlayBack.play()

                while (_isStreaming.value) {
                    audioRecords.forEachIndexed { index, record ->
                        val read = record.read(audioBuffers[index], 0, bufferSize)
                        if (read < 0) Log.e(TAG, "Error reading from mic ${record.preferredDevice?.productName}")
                    }

                    mixedBuffer.fill(0)
                    for (i in 0 until bufferSize) {
                        var sampleSum = 0
                        for (j in audioRecords.indices) {
                            sampleSum += audioBuffers[j][i]
                        }
                        mixedBuffer[i] = (sampleSum / audioRecords.size).toShort()
                    }

                    audioPlayBack.write(mixedBuffer, 0, bufferSize)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in streaming loop", e)
            } finally {
                Log.d(TAG, "Cleaning up streaming resources.")
                audioRecords.forEach {
                    it.stop()
                    it.release()
                }
                audioPlayBack.stop()
                audioPlayBack.release()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecordFor(device: AudioDeviceInfo): AudioRecord? {
        return try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            val builder = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(channelConfig).build())
                .setBufferSizeInBytes(minBufferSize)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setAudioSource(MediaRecorder.AudioSource.MIC)
                val audioRecord = builder.build()
                audioRecord.preferredDevice = device
                 if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "Successfully created AudioRecord for ${device.productName}")
                    audioRecord
                } else {
                    Log.e(TAG, "Failed to create AudioRecord for ${device.productName}")
                    null
                }
            } else {
                // For older APIs, we can't select a specific device, so we create a generic one.
                builder.setAudioSource(MediaRecorder.AudioSource.MIC)
                val audioRecord = builder.build()
                 if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "Successfully created generic AudioRecord")
                    audioRecord
                } else {
                    Log.e(TAG, "Failed to create generic AudioRecord")
                    null
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating AudioRecord for ${device.productName}", e)
            null
        }
    }

    private fun isMicrophone(device: AudioDeviceInfo): Boolean {
        return device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
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
