package com.thivyanstudios.hark.audio.processor

import android.media.AudioRecord
import android.media.AudioTrack
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.stream.AudioSink
import com.thivyanstudios.hark.audio.stream.AudioSource
import kotlinx.coroutines.channels.Channel

/**
 * A simplified Java-based audio processor used only as a fallback 
 * if the high-performance Native (Oboe) engine fails to start.
 */
class DefaultAudioProcessor(private val events: Channel<AudioEngineEvent>) : AudioProcessor {

    @Volatile
    private var currentConfig = AudioProcessingConfig()
    
    // Pre-allocated buffer to reduce GC pressure during audio processing
    private var processingBuffer: FloatArray? = null

    override fun process(
        audioSource: AudioSource,
        audioSink: AudioSink,
        config: AudioProcessingConfig,
        isRunning: () -> Boolean
    ) {
        currentConfig = config
        setupAudioEffects()

        val bufferSize = audioSink.bufferSizeInFrames
        
        // Only allocate if necessary
        if (processingBuffer == null || processingBuffer?.size != bufferSize) {
            processingBuffer = FloatArray(bufferSize)
        }
        
        val buffer = processingBuffer!!

        while (isRunning()) {
            val read = audioSource.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read > 0) {
                applyGain(buffer, read)
                audioSink.write(buffer, 0, read, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    override fun updateConfig(config: AudioProcessingConfig) {
        currentConfig = config
    }

    private fun setupAudioEffects() {
        // We notify the UI that hardware/native features are preferred
        events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(true))
        events.trySend(AudioEngineEvent.DynamicsProcessingAvailability(true))
    }

    private fun applyGain(buffer: FloatArray, size: Int) {
        val currentGain = currentConfig.microphoneGain
        val ambientGain = currentConfig.ambientGain
        
        for (i in 0 until size) {
            // In the fallback processor, we simulate the same logic as native
            // Primary path (gain) + Ambient path (ambientGain)
            val primary = buffer[i] * currentGain
            val ambient = buffer[i] * ambientGain
            
            // Basic mix and clip
            val mixed = primary + ambient
            buffer[i] = mixed.coerceIn(-1.0f, 1.0f)
        }
    }

    override fun release() {
        processingBuffer = null
    }

    companion object {
        private const val TAG = "DefaultAudioProcessor"
    }
}
