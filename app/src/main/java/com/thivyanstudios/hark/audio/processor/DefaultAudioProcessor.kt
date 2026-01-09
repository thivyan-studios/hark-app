package com.thivyanstudios.hark.audio.processor

import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.util.Log
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.stream.AudioSink
import com.thivyanstudios.hark.audio.stream.AudioSource
import kotlinx.coroutines.channels.Channel
import kotlin.math.max
import kotlin.math.min

/**
 * A simplified Java-based audio processor used only as a fallback 
 * if the high-performance Native (Oboe) engine fails to start.
 */
class DefaultAudioProcessor(private val events: Channel<AudioEngineEvent>) : AudioProcessor {

    private var equalizer: Equalizer? = null
    
    @Volatile
    private var currentConfig = AudioProcessingConfig()

    override fun process(
        audioSource: AudioSource,
        audioSink: AudioSink,
        config: AudioProcessingConfig,
        isRunning: () -> Boolean
    ) {
        currentConfig = config
        setupAudioEffects(audioSink.audioSessionId)

        val bufferSize = audioSink.bufferSizeInFrames
        val buffer = FloatArray(bufferSize)

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
        updateEqualizerBands()
        
        // Note: Noise Suppression and Dynamics Processing are now primarily 
        // handled in the Native C++ engine for better performance.
    }

    private fun setupAudioEffects(outputSessionId: Int) {
        setupEqualizer(outputSessionId)
        
        // We notify the UI that hardware/native features are preferred
        events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(true))
        events.trySend(AudioEngineEvent.DynamicsProcessingAvailability(true))
    }

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true
            updateEqualizerBands()
        } catch (e: Exception) {
             Log.e(TAG, "Failed to create Equalizer", e)
        }
    }

    private fun updateEqualizerBands() {
        val eq = equalizer ?: return
        val bands = currentConfig.equalizerBands
        if (bands.isEmpty()) return

        val numBands = eq.numberOfBands
        val minEqLevel = eq.bandLevelRange[0]
        val maxEqLevel = eq.bandLevelRange[1]

        for (i in 0 until min(bands.size, numBands.toInt())) {
            val gainDb = bands[i]
            val gainmB = (gainDb * 100).toInt().toShort()
            val safeGain = max(minEqLevel.toInt(), min(maxEqLevel.toInt(), gainmB.toInt())).toShort()
            eq.setBandLevel(i.toShort(), safeGain)
        }
    }

    private fun applyGain(buffer: FloatArray, size: Int) {
        val currentGain = currentConfig.microphoneGain
        for (i in 0 until size) {
            buffer[i] *= currentGain
        }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }

    companion object {
        private const val TAG = "DefaultAudioProcessor"
    }
}
