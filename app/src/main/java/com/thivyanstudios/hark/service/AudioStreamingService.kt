package com.thivyanstudios.hark.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.data.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import kotlin.math.pow

@AndroidEntryPoint
class AudioStreamingService : Service() {

    private val binder = LocalBinder()
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming = _isStreaming.asStateFlow()
    private var wakeLock: PowerManager.WakeLock? = null
    
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    
    @Inject
    lateinit var audioEngine: AudioEngine

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var disableHearingAidPriority = false

    private val _hearingAidConnected = MutableStateFlow(false)
    val hearingAidConnected = _hearingAidConnected.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateHearingAidStatus()
            if (_isStreaming.value) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    restartStreaming()
                }
            }
        }

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

        userPreferencesRepository.disableHearingAidPriority
            .onEach { newValue ->
                val hasChanged = newValue != disableHearingAidPriority
                disableHearingAidPriority = newValue
                if (hasChanged && _isStreaming.value) {
                    stopStreaming()
                }
                updateHearingAidStatus()
            }
            .launchIn(serviceScope)

        userPreferencesRepository.microphoneGain
            .onEach { 
                val gain = 10.0.pow(it / 20.0).toFloat()
                audioEngine.setMicrophoneGain(gain)
            }
            .launchIn(serviceScope)

        userPreferencesRepository.noiseSuppressionEnabled
            .onEach { isEnabled ->
                audioEngine.setNoiseSuppressionEnabled(isEnabled)
            }
            .launchIn(serviceScope)
            
        audioEngine.events.receiveAsFlow()
            .onEach { event ->
                when(event) {
                    is AudioEngineEvent.NoiseSuppressorNotAvailable -> {
                         Handler(Looper.getMainLooper()).post {
                             android.widget.Toast.makeText(this@AudioStreamingService, getString(R.string.noise_suppression_not_available), android.widget.Toast.LENGTH_SHORT).show()
                         }
                    }
                }
            }
            .launchIn(serviceScope)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hark::AudioStreamingWakeLock")
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    private fun updateHearingAidStatus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val deviceType = if(disableHearingAidPriority) AudioDeviceInfo.TYPE_BLUETOOTH_SCO else AudioDeviceInfo.TYPE_HEARING_AID
        _hearingAidConnected.value = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type == deviceType }
    }

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

        startForeground(NOTIFICATION_ID, notificationHelper.createNotification())
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)

        audioEngine.start()
    }

    fun stopStreaming() {
        if (!_isStreaming.value) return
        Log.d(TAG, "stopStreaming called.")
        _isStreaming.value = false

        audioEngine.stop()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val TAG = "AudioStreamingService"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopStreaming()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

}
