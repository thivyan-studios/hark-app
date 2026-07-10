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

/**
 * Orchestrates audio processing between Native (Oboe/C++) and Java/Kotlin fallbacks.
 * Also handles Whisper AI transcription lifecycle.
 */
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
        } catch (e: Exception) {
            HarkLog.e(TAG, "Failed to load native library", e)
        }
    }

    fun start() {
        if (_isStreaming.value) return
        
        if (!isNativeLibraryLoaded) {
            loadNativeLibrary()
        }

        // Task 3: Create native engine instance only when starting to avoid leaks and unnecessary resource usage
        if (isNativeLibraryLoaded && nativeHandle == 0L) {
            nativeHandle = nativeCreate()
        }

        val sampleRate = getOptimalSampleRate()
        val framesPerBurst = getOptimalFramesPerBurst()

        _isStreaming.value = true
        
        var success = false
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            try {
                success = nativeStart(nativeHandle, sampleRate, framesPerBurst)
            } catch (e: UnsatisfiedLinkError) {
                HarkLog.e(TAG, "Native start failed", e)
            }
        }
        
        if (!success) {
            HarkLog.w(TAG, "Falling back to Java/Kotlin audio engine")
            sendError("High-Performance Engine unavailable. Using fallback.")
            streamManager.start(currentConfig)
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
                // Task 3: Explicitly delete native engine instance when stopping
                nativeDelete(nativeHandle)
                nativeHandle = 0
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

    // --- Configuration Methods ---

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
    
    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        applyToNative { nativeSetDynamicsProcessingEnabled(it, enabled) }
        streamManager.updateConfig(currentConfig)
    }

    fun setTranscriptModeEnabled(enabled: Boolean) {
        applyToNative { nativeSetTranscriptModeEnabled(it, enabled) }
    }

    // --- Transcription Data Access ---

    fun readTranscriptionData(target: FloatArray, offset: Int = 0, numFrames: Int = target.size): Int {
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            return try {
                nativeReadTranscriptionData(nativeHandle, target, offset, numFrames)
            } catch (e: UnsatisfiedLinkError) { 0 }
        }
        return 0
    }

    fun getTranscriptionLevel(): Float {
        if (isNativeLibraryLoaded && nativeHandle != 0L) {
            return try {
                nativeGetTranscriptionLevel(nativeHandle)
            } catch (e: UnsatisfiedLinkError) { 0.0f }
        }
        return 0.0f
    }

    // --- Whisper AI Bridge ---

    fun initWhisper(modelPath: String): Boolean {
        if (!isNativeLibraryLoaded) return false
        return try {
            nativeInitWhisper(modelPath)
        } catch (e: UnsatisfiedLinkError) { false }
    }

    fun transcribe(audioData: FloatArray, threads: Int, language: String, translate: Boolean): String {
        if (!isNativeLibraryLoaded) return "ERROR: Lib not loaded"
        return try {
            nativeTranscribe(audioData, audioData.size, threads, language, translate)
        } catch (e: UnsatisfiedLinkError) { "ERROR: JNI fail" }
    }

    fun releaseWhisper() {
        if (isNativeLibraryLoaded) {
            try { nativeReleaseWhisper() } catch (_: UnsatisfiedLinkError) {}
        }
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
    private external fun nativeSetAmbientGain(handle: Long, gain: Float)
    private external fun nativeSetNoiseSuppressionEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetDynamicsProcessingEnabled(handle: Long, enabled: Boolean)
    private external fun nativeSetTranscriptModeEnabled(handle: Long, enabled: Boolean)
    private external fun nativeReadTranscriptionData(handle: Long, target: FloatArray, offset: Int, numFrames: Int): Int
    private external fun nativeGetTranscriptionLevel(handle: Long): Float

    private external fun nativeInitWhisper(modelPath: String): Boolean
    private external fun nativeTranscribe(audioData: FloatArray, len: Int, threads: Int, language: String, translate: Boolean): String
    private external fun nativeReleaseWhisper()

    companion object {
        private const val TAG = "AudioEngine"
    }
}
