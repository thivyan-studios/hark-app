package com.thivyanstudios.hark.audio

import android.content.Context
import android.media.AudioManager
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.processor.AudioProcessor
import com.thivyanstudios.hark.audio.stream.AudioStreamManager
import com.thivyanstudios.hark.di.IoDispatcher
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

@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioProcessor: AudioProcessor,
    private val streamManager: AudioStreamManager,
    val events: Channel<AudioEngineEvent>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isNativeLibraryLoaded = false
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
            nativeHandle = nativeCreate()
            isNativeLibraryLoaded = nativeHandle != 0L
            if (isNativeLibraryLoaded) {
                HarkLog.i(TAG, "Native library loaded and engine created: $nativeHandle")
            }
        } catch (e: Exception) {
            HarkLog.e(TAG, "Failed to load native library", e)
        }
    }

    fun start() {
        if (_isStreaming.value) return

        if (!isNativeLibraryLoaded) {
            loadNativeLibrary()
        }

        val sampleRate = getOptimalSampleRate()
        val framesPerBurst = getOptimalFramesPerBurst()

        _isStreaming.value = true

        // Use Java engine for all devices - Oboe dual-stream DataCallback is unreliable
        // on USB-C audio adapters (Samsung S21+) and older devices
        HarkLog.i(TAG, "Using Java AudioRecord/AudioTrack engine (sampleRate=$sampleRate)")
        streamManager.start(currentConfig)
        HarkLog.i(TAG, "Java engine started")
        return

        @Suppress("UNREACHABLE_CODE")
        var success = false
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try {
                HarkLog.i(TAG, "Attempting native engine start (sampleRate=$sampleRate, framesPerBurst=$framesPerBurst)")
                success = nativeStart(nativeHandle, sampleRate, framesPerBurst)
                HarkLog.i(TAG, "Native engine start result: $success")
            } catch (e: UnsatisfiedLinkError) {
                HarkLog.e(TAG, "Native start failed", e)
            }
        } else {
            HarkLog.w(TAG, "Native library not loaded (loaded=$isNativeLibraryLoaded, handle=$nativeHandle)")
        }

        if (!success) {
            HarkLog.w(TAG, "Falling back to Java/Kotlin audio engine")
            sendError("High-Performance Engine unavailable. Using fallback.")
            streamManager.start(currentConfig)
            HarkLog.i(TAG, "Fallback Java engine started")
        } else {
            HarkLog.i(TAG, "Native Oboe engine running")
        }
    }

    private fun getOptimalSampleRate(): Int {
        return audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
    }

    private fun getOptimalFramesPerBurst(): Int {
        return audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
    }

    fun stop() {
        if (!_isStreaming.value) return
        _isStreaming.value = false

        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try {
                nativeStop(nativeHandle)
            } catch (e: UnsatisfiedLinkError) {
                HarkLog.e(TAG, "Native stop failed", e)
            }
        }
        streamManager.stop()
        audioProcessor.release()
    }

    fun sendError(message: String) {
        _errorEvents.tryEmit(message)
    }

    fun setMicrophoneGain(gain: Float) {
        currentConfig = currentConfig.copy(microphoneGain = gain)
        applyToNative { nativeSetMicrophoneGain(it, gain) }
        streamManager.updateConfig(currentConfig)
    }

    fun setAmbientGain(gain: Float) {
        currentConfig = currentConfig.copy(ambientGain = gain)
        applyToNative { nativeSetAmbientGain(it, gain) }
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
        applyToNative { nativeSetNoiseSuppressionEnabled(it, enabled) }
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseReductionEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(noiseReductionEnabled = enabled)
        streamManager.updateConfig(currentConfig)
    }

    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        applyToNative { nativeSetDynamicsProcessingEnabled(it, enabled) }
        streamManager.updateConfig(currentConfig)
    }

    fun setTranscriptModeEnabled(enabled: Boolean) {
        applyToNative { nativeSetTranscriptModeEnabled(it, enabled) }
    }

    fun getInputLevel(): Float {
        val native = if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try { nativeGetInputLevel(nativeHandle) } catch (e: UnsatisfiedLinkError) { 0f }
        } else 0f
        return if (native > 0f) native else (audioProcessor as? com.thivyanstudios.hark.audio.processor.DefaultAudioProcessor)?.getInputLevel() ?: 0f
    }
    fun getOutputLevel(): Float {
        val native = if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try { nativeGetOutputLevel(nativeHandle) } catch (e: UnsatisfiedLinkError) { 0f }
        } else 0f
        return if (native > 0f) native else (audioProcessor as? com.thivyanstudios.hark.audio.processor.DefaultAudioProcessor)?.getOutputLevel() ?: 0f
    }

    private inline fun applyToNative(action: (Long) -> Unit) {
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try { action(nativeHandle) } catch (_: UnsatisfiedLinkError) {}
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDelete(handle: Long)
    private external fun nativeStart(handle: Long, sampleRate: Int, framesPerBurst: Int): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeSetMicrophoneGain(handle: Long, gain: Float)
    private external fun nativeSetAmbientGain(handle: Long, gain: Float)
    private external fun nativeSetNoiseSuppressionEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetDynamicsProcessingEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetTranscriptModeEnabled(handle: Long, enabled: Boolean)
    private external fun nativeReadTranscriptionData(handle: Long, target: FloatArray, offset: Int, numFrames: Int): Int
    private external fun nativeGetTranscriptionLevel(handle: Long): Float
    private external fun nativeGetInputLevel(handle: Long): Float
    private external fun nativeGetOutputLevel(handle: Long): Float

    companion object {
        private const val TAG = "AudioEngine"
    }
}
