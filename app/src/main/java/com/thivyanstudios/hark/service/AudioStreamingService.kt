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

    private val _transcription = MutableStateFlow("")
    override val transcription = _transcription.asStateFlow()

    private val _activeSoundEvents = MutableStateFlow<List<com.thivyanstudios.hark.ui.SoundEvent>>(emptyList())
    override val activeSoundEvents = _activeSoundEvents.asStateFlow()

    private val fullTranscript = StringBuilder()
    private var lastTranscriptionTime = 0L

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
                    // Initialize Whisper before starting engine
                    prepareWhisper()
                    audioEngine.start()
                    startTranscriptionLoop()
                }
            } catch (e: Exception) {
                HarkLog.e(TAG, "Failed to start streaming", e)
                stopStreaming()
            }
        }
    }

    private suspend fun prepareWhisper() = withContext(ioDispatcher) {
        val modelName = "ggml-tiny.en-q5_1.bin"
        val modelFile = java.io.File(filesDir, modelName)
        if (!modelFile.exists()) {
            HarkLog.i(TAG, "Copying Whisper model from assets...")
            assets.open(modelName).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        HarkLog.i(TAG, "Initializing Whisper with model: ${modelFile.absolutePath}")
        val success = audioEngine.initWhisper(modelFile.absolutePath)
        if (!success) {
            HarkLog.e(TAG, "Failed to initialize Whisper")
        }
    }

    override fun clearTranscription() {
        fullTranscript.setLength(0)
        _transcription.value = ""
        _activeSoundEvents.value = emptyList()
    }

    private fun updateSoundEvents(transcription: String) {
        val currentEvents = _activeSoundEvents.value.toMutableList()
        val now = System.currentTimeMillis()
        
        // Remove events older than 3 seconds
        currentEvents.removeAll { now - it.timestamp > 3000 }

        val lowerText = transcription.lowercase()
        val detectedLabels = mutableListOf<String>()

        if (lowerText.contains("[music]") || lowerText.contains("♪")) detectedLabels.add("Music")
        if (lowerText.contains("[laughing]") || lowerText.contains("(laughter)")) detectedLabels.add("Laughter")
        if (lowerText.contains("[clapping]") || lowerText.contains("[applause]")) detectedLabels.add("Applause")
        if (lowerText.contains("[doorbell]")) detectedLabels.add("Doorbell")
        if (lowerText.contains("[dog barking]")) detectedLabels.add("Dog Bark")
        if (lowerText.contains("[siren]")) detectedLabels.add("Siren")

        detectedLabels.forEach { label ->
            if (currentEvents.none { it.label == label }) {
                currentEvents.add(com.thivyanstudios.hark.ui.SoundEvent(label, now))
            } else {
                // Update timestamp for existing event to keep it alive
                val index = currentEvents.indexOfFirst { it.label == label }
                currentEvents[index] = currentEvents[index].copy(timestamp = now)
            }
        }

        _activeSoundEvents.value = currentEvents
    }

    private fun startTranscriptionLoop() {
        serviceScope.launch(ioDispatcher) {
            // Get current preferences
            var threads = 4
            var language = "en"
            var translate = false

            // Subscribe to preference changes
            launch {
                userPreferencesRepository.userPreferencesFlow.collect { prefs ->
                    threads = prefs.whisperThreads
                    language = prefs.whisperLanguage
                    translate = prefs.whisperTranslate
                }
            }

            // Increased buffer to 15 seconds to handle potential processing delays
            val maxBufferSize = 16000 * 15 
            val audioBuffer = FloatArray(maxBufferSize)
            var accumulatedSamples = 0
            
            fullTranscript.setLength(0)
            
            while (_isStreaming.value) {
                // 1. Read available data from the native FIFO
                val spaceRemaining = maxBufferSize - accumulatedSamples
                if (spaceRemaining > 0) {
                    val read = audioEngine.readTranscriptionData(audioBuffer, accumulatedSamples, spaceRemaining)
                    if (read > 0) {
                        accumulatedSamples += read
                    }
                }
                
                // 2. Decide if we should transcribe
                // We target chunks of ~1.0s to 1.5s for a good balance of latency and context
                if (accumulatedSamples >= 16000) { 
                    val startTime = System.currentTimeMillis()
                    val audioToProcess = audioBuffer.copyOfRange(0, accumulatedSamples)
                    
                    val result = audioEngine.transcribe(
                        audioData = audioToProcess,
                        threads = threads,
                        language = language,
                        translate = translate
                    )
                    val duration = System.currentTimeMillis() - startTime
                    
                    // Basic sound event detection based on Whisper results
                    updateSoundEvents(result)

                    val isSilence = result.contains("[SILENCE]") || result.contains("[EMPTY]")
                    val isNoResult = result.contains("[NO_RESULT]")
                    val isError = result.startsWith("ERROR")
                    
                    if (!isError && !isSilence && !isNoResult && result.isNotBlank()) {
                        val cleanedResult = result.trim()
                        if (cleanedResult.isNotEmpty()) {
                            if (fullTranscript.isNotEmpty()) {
                                fullTranscript.append(" ")
                            }
                            fullTranscript.append(cleanedResult)
                            _transcription.value = fullTranscript.toString()
                            lastTranscriptionTime = System.currentTimeMillis()
                        }
                        // On success, we consume all audio used
                        accumulatedSamples = 0
                    } else if (isSilence || isNoResult) {
                        // If it's silent/empty, we don't want the buffer to grow indefinitely.
                        // If we have more than 2.5s of audio that's been ruled silent, 
                        // keep only the last 500ms to maintain potential word-start context.
                        if (accumulatedSamples >= 40000) {
                            val keepSamples = 8000 // 500ms
                            System.arraycopy(audioBuffer, accumulatedSamples - keepSamples, audioBuffer, 0, keepSamples)
                            accumulatedSamples = keepSamples
                        }
                    } else if (accumulatedSamples >= maxBufferSize - 16000) {
                        // Safety: if buffer is nearly full and we still have no result,
                        // we're likely in a very noisy environment or falling way behind.
                        // Discard half the buffer to recover.
                        val discardSize = maxBufferSize / 2
                        System.arraycopy(audioBuffer, discardSize, audioBuffer, 0, maxBufferSize - discardSize)
                        accumulatedSamples = maxBufferSize - discardSize
                        HarkLog.w(TAG, "Transcription buffer overflow, discarding old data.")
                    }
                    
                    // Adaptive delay: if transcription is slow, don't wait as long
                    val loopDelay = if (duration > 1000) 100L else 300L
                    kotlinx.coroutines.delay(loopDelay)
                } else {
                    // Not enough data yet, wait a bit
                    kotlinx.coroutines.delay(200)
                }
            }
        }
    }

    override fun stopStreaming() {
        if (!_isStreaming.value) return
        
        HarkLog.i(TAG, "Stopping streaming")
        _isStreaming.value = false
        abandonAudioFocus()
        
        fullTranscript.setLength(0)
        _transcription.value = ""

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
                audioEngine.releaseWhisper()
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
