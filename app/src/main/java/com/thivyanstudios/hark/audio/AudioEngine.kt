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

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents = _errorEvents.asSharedFlow()

    private var currentConfig = AudioProcessingConfig()

    private fun loadNativeLibrary() {
        if (isNativeLibraryLoaded) return
        try {
            System.loadLibrary("hark")
            nativeInit()
            isNativeLibraryLoaded = true
            HarkLog.i(TAG, "Native library loaded successfully")
        } catch (e: Exception) {
            HarkLog.e(TAG, "Failed to load native library", e)
        }
    }

    fun start() {
        if (_isStreaming.value) return
        
        loadNativeLibrary()

        val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val sampleRate = sampleRateStr?.toIntOrNull() ?: 48000
        val framesPerBurstStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        val framesPerBurst = framesPerBurstStr?.toIntOrNull() ?: 192

        _isStreaming.value = true
        
        var success = false
        if (isNativeLibraryLoaded) {
            try {
                success = nativeStart(sampleRate, framesPerBurst)
            } catch (e: UnsatisfiedLinkError) {
                HarkLog.e(TAG, "Native start failed: UnsatisfiedLinkError", e)
            }
        }
        
        if (!success) {
            HarkLog.w(TAG, "Falling back to Java/Kotlin audio engine")
            sendError("Failed to start High-Performance Engine. Using fallback.")
            streamManager.start(currentConfig)
        } else {
            HarkLog.i(TAG, "Native audio engine started successfully")
        }
    }

    fun stop() {
        if (!_isStreaming.value) return
        _isStreaming.value = false
        
        if (isNativeLibraryLoaded) {
            try {
                nativeStop()
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
        if (currentConfig.microphoneGain == gain) return
        currentConfig = currentConfig.copy(microphoneGain = gain)
        
        if (isNativeLibraryLoaded) {
            try { nativeSetMicrophoneGain(gain) } catch (_: UnsatisfiedLinkError) {}
        }
        streamManager.updateConfig(currentConfig)
    }

    fun setAmbientGain(gain: Float) {
        if (currentConfig.ambientGain == gain) return
        currentConfig = currentConfig.copy(ambientGain = gain)
        
        if (isNativeLibraryLoaded) {
            try { nativeSetAmbientGain(gain) } catch (_: UnsatisfiedLinkError) {}
        }
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        if (currentConfig.noiseSuppressionEnabled == enabled) return
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
        
        if (isNativeLibraryLoaded) {
            try { nativeSetNoiseSuppressionEnabled(enabled) } catch (_: UnsatisfiedLinkError) {}
        }
        streamManager.updateConfig(currentConfig)
    }
    
    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        if (currentConfig.dynamicsProcessingEnabled == enabled) return
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        
        if (isNativeLibraryLoaded) {
            try { nativeSetDynamicsProcessingEnabled(enabled) } catch (_: UnsatisfiedLinkError) {}
        }
        streamManager.updateConfig(currentConfig)
    }

    fun setTranscriptModeEnabled(enabled: Boolean) {
        if (isNativeLibraryLoaded) {
            try { nativeSetTranscriptModeEnabled(enabled) } catch (_: UnsatisfiedLinkError) {}
        }
    }

    fun readTranscriptionData(target: FloatArray, offset: Int = 0, numFrames: Int = target.size): Int {
        if (isNativeLibraryLoaded) {
            return try {
                nativeReadTranscriptionData(target, offset, numFrames)
            } catch (e: UnsatisfiedLinkError) {
                0
            }
        }
        return 0
    }

    fun initWhisper(modelPath: String): Boolean {
        loadNativeLibrary()
        return if (isNativeLibraryLoaded) {
            try {
                nativeInitWhisper(modelPath)
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        } else false
    }

    fun transcribe(audioData: FloatArray, threads: Int, language: String, translate: Boolean, len: Int = audioData.size): String {
        return if (isNativeLibraryLoaded) {
            try {
                nativeTranscribe(audioData, len, threads, language, translate)
            } catch (e: UnsatisfiedLinkError) {
                "ERROR: JNI fail"
            }
        } else "ERROR: Lib not loaded"
    }

    fun releaseWhisper() {
        if (isNativeLibraryLoaded) {
            try {
                nativeReleaseWhisper()
            } catch (_: UnsatisfiedLinkError) {}
        }
    }

    // Native methods
    private external fun nativeInit()
    private external fun nativeStart(sampleRate: Int, framesPerBurst: Int): Boolean
    private external fun nativeStop()
    private external fun nativeSetMicrophoneGain(gain: Float)
    private external fun nativeSetAmbientGain(gain: Float)
    private external fun nativeSetNoiseSuppressionEnabled(enabled: Boolean)
    private external fun nativeSetDynamicsProcessingEnabled(enabled: Boolean)
    private external fun nativeSetTranscriptModeEnabled(enabled: Boolean)
    private external fun nativeReadTranscriptionData(target: FloatArray, offset: Int, numFrames: Int): Int
    private external fun nativeInitWhisper(modelPath: String): Boolean
    private external fun nativeTranscribe(audioData: FloatArray, len: Int, threads: Int, language: String, translate: Boolean): String
    private external fun nativeReleaseWhisper()

    companion object {
        private const val TAG = "AudioEngine"
    }
}
