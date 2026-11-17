package com.thivyanstudios.hark.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresPermission
import com.thivyanstudios.hark.BuildConfig
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.data.STTModelManager
import com.thivyanstudios.hark.data.TranscriptionRepository
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.di.IoDispatcher
import com.thivyanstudios.hark.di.MainDispatcher
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.pow

/**
 * Runs a streaming session in the background: keeps the app alive with a foreground
 * notification, and coordinates the pieces that do the actual work.
 *
 *  - [AudioEngine]: the mic → hearing aid relay (native C++)
 *  - [SherpaTranscriber]: live captions
 *  - [AudioFocusController]: pausing for calls and alarms
 *  - [HearingDeviceMonitor]: hearing aids and headsets coming and going
 *
 * This class decides *when* things happen (start, stop, pause, settings changes); the pieces
 * above decide *how*.
 */
@AndroidEntryPoint
class AudioStreamingService : Service(), AudioStreamingController, HearingDeviceMonitor.Listener {

    @Inject lateinit var audioEngine: AudioEngine
    @Inject lateinit var transcriber: SherpaTranscriber
    @Inject lateinit var audioFocus: AudioFocusController
    @Inject lateinit var devices: HearingDeviceMonitor
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject lateinit var transcriptionRepository: TranscriptionRepository
    @Inject lateinit var sttModelManager: STTModelManager
    @Inject @MainDispatcher lateinit var mainDispatcher: CoroutineDispatcher
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private val binder = LocalBinder()
    private lateinit var serviceScope: CoroutineScope
    // Outlives serviceScope so a transcript saved during onDestroy isn't cancelled mid-write
    private val persistScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }
    private var wakeLock: PowerManager.WakeLock? = null

    // --- What the app's screens observe (AudioStreamingController) ---

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming = _isStreaming.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel = _audioLevel.asStateFlow()

    // Read through, as the injected parts don't exist yet when the service object is created
    override val transcription: StateFlow<String> get() = transcriber.transcription
    override val hearingAidConnected: StateFlow<Boolean> get() = devices.connected

    // --- Session state ---

    private var pausedByFocusLoss = false
    private var lastTranscriptModeEnabled: Boolean? = null
    private var lastSelectedModelId: String? = null

    // Start/stop/pause/resume each do their engine work in a coroutine. Chaining them
    // guarantees they run in the order they were requested (e.g. a stop issued just
    // before a restart's start always finishes first).
    private var lastLifecycleJob: Job? = null
    private var transcriptionJob: Job? = null
    private var levelMonitoringJob: Job? = null

    private fun launchLifecycle(block: suspend () -> Unit) {
        val previous = lastLifecycleJob
        lastLifecycleJob = serviceScope.launch {
            previous?.join()
            block()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioStreamingController = this@AudioStreamingService
    }

    override fun onBind(intent: Intent?): IBinder {
        HarkLog.i(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's Stop button
        if (intent?.action == ACTION_STOP) {
            HarkLog.i(TAG, "Stop tapped in notification")
            stopStreaming()
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        HarkLog.i(TAG, "onCreate")
        serviceScope = CoroutineScope(mainDispatcher + SupervisorJob())

        userPreferencesRepository.userPreferencesFlow
            .distinctUntilChanged()
            .onEach(::applyPreferences)
            .launchIn(serviceScope)

        audioEngine.events.receiveAsFlow()
            .onEach(::reportUnsupportedEffect)
            .launchIn(serviceScope)

        // Stop streaming if the model library changes (download or delete)
        sttModelManager.modelStoreUpdateTrigger
            .onEach {
                if (_isStreaming.value) {
                    HarkLog.i(TAG, "Model library changed, stopping stream")
                    stopStreaming()
                }
            }
            .launchIn(serviceScope)

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hark::AudioStreamingWakeLock")
        devices.start(listener = this)
    }

    override fun onDestroy() {
        HarkLog.i(TAG, "onDestroy")
        stopStreaming()
        serviceScope.cancel()
        devices.stop()
        super.onDestroy()
    }

    // --- Settings ---

    private fun applyPreferences(prefs: UserPreferences) {
        // Changing which devices count as a hearing aid ends the current session
        if (prefs.enableBluetoothHeadsetSupport != devices.headsetsAllowed) {
            HarkLog.i(TAG, "Bluetooth headset support preference changed: ${prefs.enableBluetoothHeadsetSupport}")
            if (_isStreaming.value) stopStreaming()
            devices.setHeadsetsAllowed(prefs.enableBluetoothHeadsetSupport)
        }

        // Switching Transcript Mode or the speech model also needs a fresh start
        if (lastTranscriptModeEnabled != null && prefs.transcriptModeEnabled != lastTranscriptModeEnabled) {
            HarkLog.i(TAG, "Transcript mode changed: ${prefs.transcriptModeEnabled}, stopping stream")
            if (_isStreaming.value) stopStreaming()
        }
        lastTranscriptModeEnabled = prefs.transcriptModeEnabled

        if (lastSelectedModelId != null && prefs.selectedModelId != lastSelectedModelId) {
            HarkLog.i(TAG, "Model selection changed: ${prefs.selectedModelId}, stopping stream")
            if (_isStreaming.value) stopStreaming()
        }
        lastSelectedModelId = prefs.selectedModelId

        // Everything else applies live, mid-stream. Gain is stored in dB, the engine wants a multiplier.
        audioEngine.setMicrophoneGain(10.0.pow(prefs.microphoneGain / 20.0).toFloat())
        audioEngine.setNoiseSuppressionEnabled(prefs.noiseSuppressionEnabled)
        audioEngine.setDynamicsProcessingEnabled(prefs.dynamicsProcessingEnabled)
        audioEngine.setTrebleBoostEnabled(prefs.trebleBoostEnabled)
        audioEngine.setTranscriptModeEnabled(prefs.transcriptModeEnabled)
    }

    private fun reportUnsupportedEffect(event: AudioEngineEvent) {
        when (event) {
            is AudioEngineEvent.NoiseSuppressorAvailability -> if (!event.isAvailable) {
                HarkLog.w(TAG, "Noise suppressor not available")
                audioEngine.sendError(getString(R.string.noise_suppression_not_available))
            }
            is AudioEngineEvent.DynamicsProcessingAvailability -> if (!event.isAvailable) {
                HarkLog.w(TAG, "Dynamics processing not available")
                audioEngine.sendError(getString(R.string.dynamics_processing_not_available))
            }
        }
    }

    // --- Devices coming and going (HearingDeviceMonitor.Listener) ---

    @SuppressLint("MissingPermission") // checked by hasRequiredPermissions()
    override fun onDevicesAdded() {
        if (_isStreaming.value && hasRequiredPermissions()) restartStreaming()
    }

    @SuppressLint("MissingPermission") // checked by hasRequiredPermissions()
    override fun onDevicesRemoved(streamTargetRemoved: Boolean) {
        if (streamTargetRemoved) {
            HarkLog.i(TAG, "Compatible device removed, stopping streaming")
            stopStreaming()
        } else if (_isStreaming.value && hasRequiredPermissions()) {
            restartStreaming()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasRecordAudio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasBluetoothConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        return hasRecordAudio && hasBluetoothConnect
    }

    // --- Start / stop ---

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    private fun restartStreaming() {
        HarkLog.i(TAG, "Restarting streaming")
        if (_isStreaming.value) {
            stopStreaming()
            startStreaming()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    override fun startStreaming() {
        if (_isStreaming.value) return

        if (!audioFocus.request(::onAudioFocusChange)) {
            HarkLog.w(TAG, "Could not acquire audio focus, aborting start")
            return
        }

        HarkLog.i(TAG, "Starting streaming")
        _isStreaming.value = true

        launchLifecycle {
            // Stopped again before this got to run (a very quick start/stop): don't bring
            // up the notification, wake lock or engine for a session that's already over
            if (!_isStreaming.value) return@launchLifecycle
            try {
                enterForeground()
                // Audio first: the hearing-aid relay shouldn't wait seconds for the speech
                // model to load. Mic audio queues in the native transcription buffer (up to
                // 10 s) while Sherpa loads, then gets transcribed.
                withContext(ioDispatcher) {
                    setNormalAudioMode()
                    audioEngine.start()
                }
                startLevelMonitoring()
                if (transcriber.prepare() && _isStreaming.value) {
                    transcriptionJob = serviceScope.launch(ioDispatcher) {
                        transcriber.run(keepRunning = { _isStreaming.value })
                    }
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
        // Save the session's transcript to History before it's cleared
        val finishedTranscript = transcriber.transcription.value
        if (finishedTranscript.isNotBlank()) {
            persistScope.launch { transcriptionRepository.insert(finishedTranscript) }
        }

        _isStreaming.value = false
        pausedByFocusLoss = false
        resetAudioRouting()
        audioFocus.abandon()
        transcriber.clear()
        _audioLevel.value = 0f

        // Drop the notification straight away so it feels instant...
        leaveForeground()

        launchLifecycle {
            // ...and again once any start still in flight has finished, since a start that
            // was mid-way when Stop was tapped may have only just posted it
            leaveForeground()
            // Wait for the loops to exit so the model is never freed mid-decode
            transcriptionJob?.cancelAndJoin()
            levelMonitoringJob?.cancelAndJoin()
            transcriptionJob = null
            levelMonitoringJob = null
            transcriber.clear()
            _audioLevel.value = 0f
            withContext(ioDispatcher) { audioEngine.stop() }
            transcriber.release()
        }
    }

    override fun clearTranscription() = transcriber.clear()

    // --- Calls and alarms (audio focus) ---

    private fun onAudioFocusChange(change: AudioFocusController.Change) {
        when (change) {
            AudioFocusController.Change.Lost -> stopStreaming()
            AudioFocusController.Change.LostTemporarily -> pauseStreaming()
            AudioFocusController.Change.Regained -> resumeStreaming()
        }
    }

    // A call, alarm or assistant has the audio for a while: keep the session (notification,
    // wake lock, captions so far) and just stop the engine until focus comes back.
    private fun pauseStreaming() {
        if (!_isStreaming.value || pausedByFocusLoss) return
        pausedByFocusLoss = true
        _audioLevel.value = 0f
        launchLifecycle { withContext(ioDispatcher) { audioEngine.stop() } }
    }

    private fun resumeStreaming() {
        if (!pausedByFocusLoss) return
        pausedByFocusLoss = false
        if (!_isStreaming.value) return
        launchLifecycle { withContext(ioDispatcher) { audioEngine.start() } }
    }

    // --- Foreground notification, level meter, routing ---

    @SuppressLint("ForegroundServiceType") // declared as "microphone" in the manifest
    private fun enterForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notificationHelper.createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notificationHelper.createNotification())
        }
        wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun leaveForeground() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun startLevelMonitoring() {
        levelMonitoringJob = serviceScope.launch(ioDispatcher) {
            var ticks = 0
            while (_isStreaming.value) {
                _audioLevel.value = audioEngine.getTranscriptionLevel()
                // Every ~2 s in debug builds, log how loud the audio reaching Sherpa is
                // (logcat tag "LevelCheck")
                if (BuildConfig.DEBUG && ++ticks % 40 == 0) {
                    audioEngine.consumeTranscriptionLevelStats()?.let { HarkLog.d("LevelCheck", it.summary()) }
                }
                delay(50L) // 20 fps for a smooth meter
            }
        }
    }

    // Normal mode: HARK plays to the default output and never uses call (SCO) routing
    private fun setNormalAudioMode() {
        (getSystemService(AUDIO_SERVICE) as AudioManager).mode = AudioManager.MODE_NORMAL
    }

    private fun resetAudioRouting() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_NORMAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
    }

    companion object {
        private const val TAG = "AudioStreamingService"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L // 1 hour
        const val ACTION_STOP = "com.thivyanstudios.hark.action.STOP_STREAMING"
    }
}
