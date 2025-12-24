package com.thivyanstudios.hark.audio

import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.processor.DefaultAudioProcessor
import com.thivyanstudios.hark.audio.stream.AudioStreamManager
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEngine @Inject constructor() {

    val events = Channel<AudioEngineEvent>(Channel.BUFFERED)

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

    fun setMicrophoneGain(gain: Float) {
        currentConfig = currentConfig.copy(microphoneGain = gain)
        streamManager.updateConfig(currentConfig)
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
        streamManager.updateConfig(currentConfig)
    }
    
    fun setEqualizerBands(bands: List<Float>) {
        currentConfig = currentConfig.copy(equalizerBands = bands)
        streamManager.updateConfig(currentConfig)
    }
    
    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        streamManager.updateConfig(currentConfig)
    }
}
