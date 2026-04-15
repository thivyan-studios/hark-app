package com.thivyanstudios.hark.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.di.IoDispatcher
import com.thivyanstudios.hark.di.MainDispatcher
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow

@AndroidEntryPoint
class AudioStreamingService : Service(), AudioStreamingController {

    private val binder = LocalBinder()
    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming = _isStreaming.asStateFlow()
    
    private var wakeLock: PowerManager.WakeLock? = null

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    
    @Inject
    lateinit var audioEngine: AudioEngine

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    @MainDispatcher
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private lateinit var serviceScope: CoroutineScope
    private var disableHearingAidPriority = false

    private val _hearingAidConnected = MutableStateFlow(false)
    override val hearingAidConnected = _hearingAidConnected.asStateFlow()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                HarkLog.i(TAG, "Audio focus lost, stopping streaming")
                stopStreaming()
            }
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            HarkLog.i(TAG, "Audio devices added")
            updateHearingAidStatus()
            if (_isStreaming.value && hasRequiredPermissions()) {
                restartStreaming()
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            HarkLog.i(TAG, "Audio devices removed")
            updateHearingAidStatus()
            
            val isTargetDeviceRemoved = removedDevices?.any { isCompatibleDevice(it.type) } == true
            if (isTargetDeviceRemoved) {
                HarkLog.i(TAG, "Compatible device removed, stopping streaming")
                stopStreaming()
            } else if (_isStreaming.value && hasRequiredPermissions()) {
                restartStreaming()
            }
        }
    }

    private fun isCompatibleDevice(type: Int): Boolean {
        return if (disableHearingAidPriority) {
            when (type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> true
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        type == AudioDeviceInfo.TYPE_BLE_HEADSET || 
                        type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    } else false
                }
            }
        } else {
            type == AudioDeviceInfo.TYPE_HEARING_AID
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasRecordAudio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasBluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return hasRecordAudio && hasBluetoothConnect
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamingController = this@AudioStreamingService
    }

    override fun onBind(intent: Intent?): IBinder {
        HarkLog.i(TAG, "onBind")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        HarkLog.i(TAG, "onCreate")
        serviceScope = CoroutineScope(mainDispatcher + SupervisorJob())

        userPreferencesRepository.userPreferencesFlow
            .distinctUntilChanged()
            .onEach { prefs ->
                val newDisablePriority = prefs.disableHearingAidPriority
                if (newDisablePriority != disableHearingAidPriority) {
                    HarkLog.i(TAG, "Hearing aid priority preference changed: $newDisablePriority")
                    disablePriorityChange(newDisablePriority)
                }

                val gain = 10.0.pow(prefs.microphoneGain / 20.0).toFloat()
                audioEngine.setMicrophoneGain(gain)
                audioEngine.setNoiseSuppressionEnabled(prefs.noiseSuppressionEnabled)
                audioEngine.setDynamicsProcessingEnabled(prefs.dynamicsProcessingEnabled)
            }
            .launchIn(serviceScope)
            
        audioEngine.events.receiveAsFlow()
            .onEach { event ->
                when(event) {
                    is AudioEngineEvent.NoiseSuppressorAvailability -> {
                        if (!event.isAvailable) {
                            HarkLog.w(TAG, "Noise suppressor not available")
                            audioEngine.sendError(getString(R.string.noise_suppression_not_available))
                        }
                    }
                    is AudioEngineEvent.DynamicsProcessingAvailability -> {
                        if (!event.isAvailable) {
                            HarkLog.w(TAG, "Dynamics processing not available")
                            audioEngine.sendError(getString(R.string.dynamics_processing_not_available))
                        }
                    }
                }
            }
            .launchIn(serviceScope)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hark::AudioStreamingWakeLock")
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        updateHearingAidStatus()
    }

    private fun disablePriorityChange(newDisablePriority: Boolean) {
        disableHearingAidPriority = newDisablePriority
        if (_isStreaming.value) {
            stopStreaming()
        }
        updateHearingAidStatus()
    }

    private fun updateHearingAidStatus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val isConnected = devices.any { isCompatibleDevice(it.type) }
        HarkLog.i(TAG, "Hearing aid connected: $isConnected")
        _hearingAidConnected.value = isConnected
    }

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    private fun restartStreaming() {
        HarkLog.i(TAG, "Restarting streaming")
        if (_isStreaming.value) {
            stopStreaming()
            startStreaming()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    @SuppressLint("ForegroundServiceType")
    override fun startStreaming() {
        if (_isStreaming.value) return

        if (!requestAudioFocus()) {
            HarkLog.w(TAG, "Could not acquire audio focus, aborting start")
            return
        }
        
        HarkLog.i(TAG, "Starting streaming")
        _isStreaming.value = true

        serviceScope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID, 
                        notificationHelper.createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notificationHelper.createNotification())
                }
                
                wakeLock?.acquire()
                withContext(ioDispatcher) {
                    audioEngine.start()
                }
            } catch (e: Exception) {
                HarkLog.e(TAG, "Failed to start streaming", e)
                stopStreaming()
            }
        }
    }

    override fun stopStreaming() {
        if (!_isStreaming.value) return
        
        HarkLog.i(TAG, "Stopping streaming")
        _isStreaming.value = false
        abandonAudioFocus()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        serviceScope.launch {
            withContext(ioDispatcher) {
                audioEngine.stop()
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    override fun onDestroy() {
        HarkLog.i(TAG, "onDestroy")
        stopStreaming()
        serviceScope.cancel()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AudioStreamingService"
        private const val NOTIFICATION_ID = 1
    }
}
