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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import kotlin.math.pow

@AndroidEntryPoint
class AudioStreamingService : Service(), AudioStreamingController {

    private val binder = LocalBinder()
    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming = _isStreaming.asStateFlow()
    private var wakeLock: PowerManager.WakeLock? = null

    // Track if we are in "test mode" so we can distinguish it in the UI if needed
    private val _isTestMode = MutableStateFlow(false)
    override val isTestMode = _isTestMode.asStateFlow()
    
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    
    @Inject
    lateinit var audioEngine: AudioEngine

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var disableHearingAidPriority = false

    private val _hearingAidConnected = MutableStateFlow(false)
    override val hearingAidConnected = _hearingAidConnected.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateHearingAidStatus()
            if (_isStreaming.value) {
                if (hasRequiredPermissions()) {
                    restartStreaming()
                }
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateHearingAidStatus()
            if (_isStreaming.value) {
                if (hasRequiredPermissions()) {
                    restartStreaming()
                }
            }
            val deviceType = if(disableHearingAidPriority) AudioDeviceInfo.TYPE_BLUETOOTH_SCO else AudioDeviceInfo.TYPE_HEARING_AID
            if (removedDevices?.any { it.type == deviceType } == true) {
                stopStreaming()
            }
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

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        // Consolidated flow collection for user preferences
        userPreferencesRepository.userPreferencesFlow
            .distinctUntilChanged()
            .onEach { prefs ->
                // Handle disableHearingAidPriority
                val newDisablePriority = prefs.disableHearingAidPriority
                if (newDisablePriority != disableHearingAidPriority) {
                    disableHearingAidPriority = newDisablePriority
                    if (_isStreaming.value) {
                        stopStreaming()
                    }
                    updateHearingAidStatus()
                }

                // Handle Audio Engine Settings
                val gain = 10.0.pow(prefs.microphoneGain / 20.0).toFloat()
                audioEngine.setMicrophoneGain(gain)
                audioEngine.setNoiseSuppressionEnabled(prefs.noiseSuppressionEnabled)
                audioEngine.setEqualizerBands(prefs.equalizerBands)
                audioEngine.setDynamicsProcessingEnabled(prefs.dynamicsProcessingEnabled)
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
                    is AudioEngineEvent.DynamicsProcessingNotAvailable -> {
                         Handler(Looper.getMainLooper()).post {
                             android.widget.Toast.makeText(this@AudioStreamingService, getString(R.string.dynamics_processing_not_available), android.widget.Toast.LENGTH_SHORT).show()
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
            stopStreaming()
            startStreaming()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    @SuppressLint("ForegroundServiceType")
    override fun startStreaming() {
        // Stop test stream if it is running to ensure we can switch to mic stream cleanly
        if (_isTestMode.value) {
            stopStreaming()
        }

        if (_isStreaming.value) {
            return
        }
        
        _isStreaming.value = true
        _isTestMode.value = false // Ensure test mode is off

        startForeground(NOTIFICATION_ID, notificationHelper.createNotification())
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)

        audioEngine.start()
    }
    
    @SuppressLint("ForegroundServiceType")
    override fun startTestStreaming() {
        // Stop mic stream if it is running to ensure we can switch to test stream cleanly
        if (_isStreaming.value) {
            stopStreaming()
        }

        if (_isStreaming.value || _isTestMode.value) {
            return
        }
        
        // Do not set isStreaming to true, we don't want the UI to update as if we are streaming mic audio
        _isTestMode.value = true // Ensure test mode is ON

        // Do not start foreground service for test mode as requested
        wakeLock?.acquire(10*60*1000L /*10 minutes*/)

        audioEngine.startTest()
    }

    override fun stopStreaming() {
        if (!_isStreaming.value && !_isTestMode.value) return
        
        _isStreaming.value = false
        _isTestMode.value = false

        audioEngine.stop()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopStreaming()
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

}