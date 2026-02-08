package com.thivyanstudios.hark.audio

import android.content.Context
import android.media.AudioManager
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.processor.DefaultAudioProcessor
import com.thivyanstudios.hark.audio.stream.AudioStreamManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _isStreaming = MutableStateFlow(false)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        try {
            System.loadLibrary("hark")
            nativeInit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val events = Channel<AudioEngineEvent>(Channel.BUFFERED)
    
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents = _errorEvents.asSharedFlow()

    private val audioProcessor = DefaultAudioProcessor(events)
    private val streamManager = AudioStreamManager(audioProcessor)

    private var currentConfig = AudioProcessingConfig()

    fun start() {
        if (_isStreaming.value) return
        
        val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val sampleRate = sampleRateStr?.toIntOrNull() ?: 48000
        val framesPerBurstStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        val framesPerBurst = framesPerBurstStr?.toIntOrNull() ?: 192

        _isStreaming.value = true
        
        val success = try {
            nativeStart(sampleRate, framesPerBurst)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
        
        if (!success) {
            sendError("Failed to start Oboe Native Engine")
            streamManager.start(currentConfig)
        }
    }

    fun stop() {
        if (!_isStreaming.value) return
        _isStreaming.value = false
        
        try {
            nativeStop()
        } catch (_: UnsatisfiedLinkError) {
            // Handle error
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
        try { nativeSetMicrophoneGain(gain) } catch (_: UnsatisfiedLinkError) {}
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        if (currentConfig.noiseSuppressionEnabled == enabled) return
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
        try { nativeSetNoiseSuppressionEnabled(enabled) } catch (_: UnsatisfiedLinkError) {}
        streamManager.updateConfig(currentConfig)
    }
    
    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        if (currentConfig.dynamicsProcessingEnabled == enabled) return
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        try { nativeSetDynamicsProcessingEnabled(enabled) } catch (_: UnsatisfiedLinkError) {}
        streamManager.updateConfig(currentConfig)
    }

    // Native methods
    private external fun nativeInit()
    private external fun nativeStart(sampleRate: Int, framesPerBurst: Int): Boolean
    private external fun nativeStop()
    private external fun nativeSetMicrophoneGain(gain: Float)
    private external fun nativeSetNoiseSuppressionEnabled(enabled: Boolean)
    private external fun nativeSetDynamicsProcessingEnabled(enabled: Boolean)
}
