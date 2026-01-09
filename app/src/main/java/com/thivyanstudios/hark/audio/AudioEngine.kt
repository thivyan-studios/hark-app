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

    val events = Channel<AudioEngineEvent>(Channel.BUFFERED)
    
    // QC: Using SharedFlow for errors/notifications that should be shown as snackbars
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents = _errorEvents.asSharedFlow()

    private val audioProcessor = DefaultAudioProcessor(events)
    private val streamManager = AudioStreamManager(audioProcessor)

    private var currentConfig = AudioProcessingConfig()

    fun start() {
        streamManager.start(currentConfig)
    }

    fun startTest() {
        streamManager.startTest(currentConfig)
    }

    fun stop() {
        streamManager.stop()
        audioProcessor.release()
    }
    
    fun sendError(message: String) {
        _errorEvents.tryEmit(message)
    }

    fun setMicrophoneGain(gain: Float) {
        if (currentConfig.microphoneGain == gain) return
        currentConfig = currentConfig.copy(microphoneGain = gain)
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        if (currentConfig.noiseSuppressionEnabled == enabled) return
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
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
        streamManager.updateConfig(currentConfig)
    }
}
