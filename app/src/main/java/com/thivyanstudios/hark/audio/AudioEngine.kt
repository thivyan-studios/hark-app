package com.thivyanstudios.hark.audio

import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.processor.DefaultAudioProcessor
import com.thivyanstudios.hark.audio.stream.AudioStreamManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngine @Inject constructor() {

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
        val success = try {
            nativeStart()
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        
        if (!success) {
            sendError("Failed to start Oboe Native Engine")
            streamManager.start(currentConfig)
        }
    }

    fun startTest() {
        streamManager.startTest(currentConfig)
    }

    fun stop() {
        try {
            nativeStop()
        } catch (e: UnsatisfiedLinkError) {
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
        try { nativeSetMicrophoneGain(gain) } catch (e: UnsatisfiedLinkError) {}
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        if (currentConfig.noiseSuppressionEnabled == enabled) return
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
        try { nativeSetNoiseSuppressionEnabled(enabled) } catch (e: UnsatisfiedLinkError) {}
        streamManager.updateConfig(currentConfig)
    }
    
    fun setEqualizerBands(bands: List<Float>) {
        if (currentConfig.equalizerBands == bands) return
        currentConfig = currentConfig.copy(equalizerBands = bands)
        streamManager.updateConfig(currentConfig)
    }
    
    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        if (currentConfig.dynamicsProcessingEnabled == enabled) return
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        try { nativeSetDynamicsProcessingEnabled(enabled) } catch (e: UnsatisfiedLinkError) {}
        streamManager.updateConfig(currentConfig)
    }

    // Native methods
    private external fun nativeInit()
    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeSetMicrophoneGain(gain: Float)
    private external fun nativeSetNoiseSuppressionEnabled(enabled: Boolean)
    private external fun nativeSetDynamicsProcessingEnabled(enabled: Boolean)
}
