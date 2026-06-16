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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow

@AndroidEntryPoint
class AudioStreamingService : Service(), AudioStreamingController {

    private val binder = LocalBinder()

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming = _isStreaming.asStateFlow()

    private var preferredOutputDevice: AudioDeviceInfo? = null
    private var preferredInputDevice: AudioDeviceInfo? = null

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel = _audioLevel.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    private val _outputLevel = MutableStateFlow(0f)
    override val inputLevel: StateFlow<Float> = _inputLevel.asStateFlow()
    override val outputLevel: StateFlow<Float> = _outputLevel.asStateFlow()

    private val _latencyMs = MutableStateFlow(0f)
    override val latencyMs: StateFlow<Float> = _latencyMs.asStateFlow()

    private val _outputDeviceName = MutableStateFlow("")
    override val outputDeviceName: StateFlow<String> = _outputDeviceName.asStateFlow()

    private val _inputDeviceName = MutableStateFlow("")
    override val inputDeviceName: StateFlow<String> = _inputDeviceName.asStateFlow()

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
    private var enableBluetoothHeadsetSupport = false
    private var preferExternalMic = false

    private val _hearingAidConnected = MutableStateFlow(false)
    override val hearingAidConnected = _hearingAidConnected.asStateFlow()

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        // A hearing aid must NEVER stop for transient focus loss (notifications, Assistant,
        // Bluetooth negotiation all fire LOSS_TRANSIENT). Log and keep running.
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                HarkLog.i(TAG, "Audio focus permanently lost - continuing (hearing aid priority)")
                // Do NOT stop - user can manually stop. Re-request focus to stay active.
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                HarkLog.i(TAG, "Audio focus transient loss (notification/assistant) - continuing")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                HarkLog.i(TAG, "Audio focus duck request - continuing at full volume")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                HarkLog.i(TAG, "Audio focus regained")
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
        return if (enableBluetoothHeadsetSupport) {
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
                val newEnableSupport = prefs.enableBluetoothHeadsetSupport
                if (newEnableSupport != enableBluetoothHeadsetSupport) {
                    HarkLog.i(TAG, "Bluetooth headset support preference changed: $newEnableSupport")
                    bluetoothSupportChange(newEnableSupport)
                }

                val newPreferExternalMic = prefs.preferExternalMic
                if (newPreferExternalMic != preferExternalMic) {
                    HarkLog.i(TAG, "Prefer external mic preference changed: $newPreferExternalMic")
                    preferExternalMic = newPreferExternalMic
                    if (_isStreaming.value) {
                        updateAudioRouting()
                    }
                }

                val gain = 10.0.pow(prefs.microphoneGain / 20.0).toFloat()
                audioEngine.setMicrophoneGain(gain)
                audioEngine.setNoiseSuppressionEnabled(prefs.noiseSuppressionEnabled)
                audioEngine.setDynamicsProcessingEnabled(prefs.dynamicsProcessingEnabled)
                audioEngine.setNoiseReductionEnabled(prefs.noiseReductionEnabled)
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

    private fun bluetoothSupportChange(newEnableSupport: Boolean) {
        enableBluetoothHeadsetSupport = newEnableSupport
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

    private fun refreshDeviceNames() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        val outputDevice = outputs.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            d.type == AudioDeviceInfo.TYPE_HEARING_AID
        }
        val inputDevice = inputs.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        } ?: inputs.firstOrNull { d -> d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

        _outputDeviceName.value = outputDevice?.productName?.toString() ?: ""
        _inputDeviceName.value = inputDevice?.productName?.toString() ?: ""
        HarkLog.i(TAG, "Device names: in='${_inputDeviceName.value}' out='${_outputDeviceName.value}'")
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
            var logCounter = 0
            var latencyCounter = 0
            while (_isStreaming.value) {
                val inLevel = audioEngine.getInputLevel()
                val outLevel = audioEngine.getOutputLevel()
                _inputLevel.value = inLevel
                _outputLevel.value = outLevel
                latencyCounter++
                if (latencyCounter % 10 == 0) {
                    _latencyMs.value = audioEngine.getLatencyMs()
                }
                logCounter++
                if (logCounter % 100 == 0) {
                    HarkLog.i(TAG, "Audio levels - IN: ${"%.4f".format(inLevel)} OUT: ${"%.4f".format(outLevel)}")
                }
                delay(50)
            }
            _inputLevel.value = 0f
            _outputLevel.value = 0f
            _latencyMs.value = 0f
        }

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
                    updateAudioRouting()
                    audioEngine.start(preferredOutputDevice, preferredInputDevice)
                }
                refreshDeviceNames()

                // Log full audio diagnostics to HarkLog (visible in app)
                serviceScope.launch {
                    delay(600)
                    val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                    HarkLog.i(TAG, "=== AUDIO DIAGNOSTICS ===")
                    HarkLog.i(TAG, "AudioManager.mode=${am.mode} wiredHeadset=${@Suppress("DEPRECATION") am.isWiredHeadsetOn} micMute=${am.isMicrophoneMute}")
                    HarkLog.i(TAG, "streamVol(MUSIC)=${am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)}")
                    val devices = am.getDevices(android.media.AudioManager.GET_DEVICES_ALL)
                    HarkLog.i(TAG, "Audio devices (${devices.size}):")
                    devices.forEach { d ->
                        HarkLog.i(TAG, "  [${if(d.isSink) "OUT" else "IN "}] type=${d.type} '${d.productName}'")
                    }
                    HarkLog.i(TAG, "=========================")
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
        clearAudioRouting()
        abandonAudioFocus()

        _audioLevel.value = 0f
        _inputLevel.value = 0f
        _outputLevel.value = 0f
        _latencyMs.value = 0f
        _outputDeviceName.value = ""
        _inputDeviceName.value = ""

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
        // ACCESSIBILITY usage is exempt from Android 12+ auto-fade-out when other apps take focus.
        // This is what keeps a hearing aid running while music/podcasts play.
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        // GAIN_TRANSIENT_MAY_DUCK: we coexist with other audio instead of killing it,
        // and other apps duck around us rather than fading us out.
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        HarkLog.i(TAG, "AudioFocus request result: $result (1=granted)")
        // Even if focus is denied, proceed - hearing aid should work regardless
        return true
    }

    private fun abandonAudioFocus() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun updateAudioRouting() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (preferExternalMic) {
            // NOTE: Do NOT set MODE_IN_COMMUNICATION - it forces Samsung into VoIP routing (earpiece)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val btMic = devices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btMic != null) {
                    HarkLog.i(TAG, "Setting communication device to: ${btMic.productName}")
                    audioManager.setCommunicationDevice(btMic)
                } else {
                    HarkLog.w(TAG, "External mic preferred but no BT mic found")
                }
            } else {
                HarkLog.i(TAG, "Starting Bluetooth SCO")
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            }
        } else {
            clearAudioRouting()
        }
    }

    private fun clearAudioRouting() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isBluetoothScoOn) {
                HarkLog.i(TAG, "Stopping Bluetooth SCO")
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
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
