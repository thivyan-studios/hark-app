package com.thivyanstudios.hark.audio

import com.thivyanstudios.hark.R
import android.content.Context
import android.media.AudioManager
import android.media.audiofx.NoiseSuppressor
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.processor.AudioProcessor
import com.thivyanstudios.hark.audio.stream.AudioStreamManager
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates audio processing between Native (Oboe/C++) and Java/Kotlin fallbacks.
 * Sherpa-ONNX is managed at the Service level using the native FIFO provided here.
 */
@Singleton
class AudioEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audioProcessor: AudioProcessor,
    private val streamManager: AudioStreamManager,
    val events: Channel<AudioEngineEvent>,
) {
    private val _isStreaming = MutableStateFlow(value = false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isNativeLibraryLoaded = false
    private var noiseSuppressor: NoiseSuppressor? = null
    
    // Native handle to the C++ HarkAudioEngine instance
    private var nativeHandle: Long = 0

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents = _errorEvents.asSharedFlow()

    private var currentConfig = AudioProcessingConfig()

    init {
        loadNativeLibrary()
    }

    private fun loadNativeLibrary() {
        if (isNativeLibraryLoaded) return
        try {
            System.loadLibrary("hark")
            isNativeLibraryLoaded = true
            HarkLog.i(TAG, "Native library loaded")
        } catch (e: UnsatisfiedLinkError) {
            // loadLibrary throws an Error, not an Exception; catching it lets start()
            // fall back to the Kotlin engine instead of crashing.
            HarkLog.e(TAG, "Failed to load native library", e)
        }
    }

    fun start() {
        if (_isStreaming.value) return
        
        if (!isNativeLibraryLoaded) {
            loadNativeLibrary()
        }

        // Create native engine instance only when starting to avoid leaks and unnecessary resource usage
        if (isNativeLibraryLoaded && (nativeHandle == 0L)) {
            nativeHandle = nativeCreate()
        }

        val sampleRate = getOptimalSampleRate()
        val framesPerBurst = getOptimalFramesPerBurst()

        _isStreaming.value = true
        
        var success = false
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try {
                // A freshly created native engine starts with every option off, so push
                // the current settings before audio begins flowing.
                applyConfigToNative(nativeHandle)
                success = nativeStart(nativeHandle, sampleRate, framesPerBurst)
                if (success) {
                    val sessionId = nativeGetSessionId(nativeHandle)
                    if (sessionId != -1) {
                        setupNoiseSuppressor(sessionId)
                    }
                }
            } catch (_: UnsatisfiedLinkError) {
                HarkLog.e(TAG, "Native start failed")
            }
        }
        
        if (!success) {
            HarkLog.w(TAG, "Falling back to Java/Kotlin audio engine")
            // The fallback only relays audio; it has no transcription feed, so say so
            sendError(context.getString(R.string.error_audio_engine_fallback))
            streamManager.start(currentConfig, getOptimalSampleRate())
        }
    }

    fun getOptimalSampleRate(): Int {
        return audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
    }

    /**
     * Sample rate of the audio handed to Sherpa: what the mic stream is actually delivering,
     * not just what was requested. Falls back to the device's standard rate.
     */
    fun getTranscriptionSampleRate(): Int {
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try {
                val rate = nativeGetInputSampleRate(nativeHandle)
                if (rate > 0) return rate
            } catch (_: UnsatisfiedLinkError) {}
        }
        return getOptimalSampleRate()
    }

    /**
     * Diagnostics for the transcription feed since the last call (then reset), or null if
     * the native engine isn't running. See [TranscriptionLevelStats].
     */
    fun consumeTranscriptionLevelStats(): TranscriptionLevelStats? {
        if (!isNativeLibraryLoaded || nativeHandle == 0L) return null
        val out = FloatArray(6)
        try { nativeConsumeLevelStats(nativeHandle, out) } catch (_: UnsatisfiedLinkError) { return null }
        val total = out[5].toInt()
        if (total == 0) return null
        return TranscriptionLevelStats(
            rawPeak = out[0],
            rawRms = kotlin.math.sqrt(out[1] / total),
            boostedPeak = out[2],
            boostedRms = kotlin.math.sqrt(out[3] / total),
            overFullScaleFraction = out[4] / total,
        )
    }

    fun getOptimalFramesPerBurst(): Int {
        return audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
    }

    fun stop() {
        if (!_isStreaming.value) return
        _isStreaming.value = false
        
        releaseNoiseSuppressor()
        
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try {
                nativeStop(nativeHandle)
                // Explicitly delete native engine instance when stopping
                nativeDelete(nativeHandle)
                nativeHandle = 0
            } catch (_: UnsatisfiedLinkError) {
                HarkLog.e(TAG, "Native stop failed")
            }
        }
        streamManager.stop()
        audioProcessor.release()
    }
    
    fun sendError(message: String) {
        _errorEvents.tryEmit(message)
    }

    // --- Configuration Methods ---

    fun setMicrophoneGain(gain: Float) {
        currentConfig = currentConfig.copy(microphoneGain = gain)
        applyToNative { nativeSetMicrophoneGain(it, gain) }
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
        applyToNative { nativeSetNoiseSuppressionEnabled(it, enabled) }
        
        noiseSuppressor?.enabled = enabled
        
        streamManager.updateConfig(currentConfig)
    }
    
    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        applyToNative { nativeSetDynamicsProcessingEnabled(it, enabled) }
        streamManager.updateConfig(currentConfig)
    }

    fun setTranscriptModeEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(transcriptModeEnabled = enabled)
        applyToNative { nativeSetTranscriptModeEnabled(it, enabled) }
    }

    fun setTrebleBoostEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(trebleBoostEnabled = enabled)
        applyToNative { nativeSetTrebleBoostEnabled(it, enabled) }
    }

    private fun applyConfigToNative(handle: Long) {
        val config = currentConfig
        nativeSetMicrophoneGain(handle, config.microphoneGain)
        nativeSetNoiseSuppressionEnabled(handle, config.noiseSuppressionEnabled)
        nativeSetDynamicsProcessingEnabled(handle, config.dynamicsProcessingEnabled)
        nativeSetTrebleBoostEnabled(handle, config.trebleBoostEnabled)
        nativeSetTranscriptModeEnabled(handle, config.transcriptModeEnabled)
    }

    private fun setupNoiseSuppressor(sessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            try {
                releaseNoiseSuppressor()
                noiseSuppressor = NoiseSuppressor.create(sessionId).apply {
                    enabled = currentConfig.noiseSuppressionEnabled
                }
                HarkLog.i(TAG, "System NoiseSuppressor initialized on session $sessionId")
            } catch (e: Exception) {
                HarkLog.e(TAG, "Failed to create NoiseSuppressor", e)
            }
        } else {
            HarkLog.w(TAG, "System NoiseSuppressor is not available on this device")
        }
    }

    private fun releaseNoiseSuppressor() {
        noiseSuppressor?.release()
        noiseSuppressor = null
    }

    // --- Transcription Data Access ---

    fun readTranscriptionData(target: FloatArray, offset: Int = 0, numFrames: Int = target.size): Int {
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            return try {
                nativeReadTranscriptionData(nativeHandle, target, offset, numFrames)
            } catch (_: UnsatisfiedLinkError) { 0 }
        }
        return 0
    }

    fun getTranscriptionLevel(): Float {
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            return try {
                nativeGetTranscriptionLevel(nativeHandle)
            } catch (_: UnsatisfiedLinkError) { 0.0f }
        }
        return 0.0f
    }

    private inline fun applyToNative(action: (Long) -> Unit) {
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try { action(nativeHandle) } catch (_: UnsatisfiedLinkError) {}
        }
    }

    // --- Native Definitions ---

    private external fun nativeCreate(): Long
    private external fun nativeDelete(handle: Long)
    private external fun nativeStart(handle: Long, sampleRate: Int, framesPerBurst: Int): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeSetMicrophoneGain(handle: Long, gain: Float)
    private external fun nativeSetNoiseSuppressionEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetDynamicsProcessingEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetTranscriptModeEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetTrebleBoostEnabled(handle: Long, enabled: Boolean)
    private external fun nativeReadTranscriptionData(handle: Long, target: FloatArray, offset: Int, numFrames: Int): Int
    private external fun nativeGetTranscriptionLevel(handle: Long): Float
    private external fun nativeGetSessionId(handle: Long): Int
    private external fun nativeGetInputSampleRate(handle: Long): Int
    private external fun nativeConsumeLevelStats(handle: Long, out: FloatArray)

    companion object {
        private const val TAG = "AudioEngine"
    }
}

/**
 * Loudness of the audio going to Sherpa over a short window (linear, 1.0 = full scale).
 * "raw" is the mic as captured; "boosted" is after the transcription gain, i.e. what Sherpa hears.
 */
data class TranscriptionLevelStats(
    val rawPeak: Float,
    val rawRms: Float,
    val boostedPeak: Float,
    val boostedRms: Float,
    val overFullScaleFraction: Float,
) {
    private fun db(x: Float) = if (x <= 0f) -120f else 20f * kotlin.math.log10(x)

    fun summary(): String = String.format(
        java.util.Locale.US,
        "mic rms %.1f dBFS (peak %.1f) | to Sherpa rms %.1f dBFS (peak %.1f) | over full scale %.2f%%",
        db(rawRms), db(rawPeak), db(boostedRms), db(boostedPeak), overFullScaleFraction * 100f,
    )
}
