package com.thivyanstudios.hark.audio.processor

import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.stream.AudioSink
import com.thivyanstudios.hark.audio.stream.AudioSource
import kotlinx.coroutines.channels.Channel
import kotlin.math.max
import kotlin.math.min

class DefaultAudioProcessor(private val events: Channel<AudioEngineEvent>) : AudioProcessor {

    private var noiseSuppressor: NoiseSuppressor? = null
    private var equalizer: Equalizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null
    
    private var isNoiseSuppressorSupported = false
    private var isDynamicsProcessingSupported = false
    
    @Volatile
    private var currentConfig = AudioProcessingConfig()

    override fun process(
        audioSource: AudioSource,
        audioSink: AudioSink,
        config: AudioProcessingConfig,
        isRunning: () -> Boolean
    ) {
        currentConfig = config
        setupAudioEffects(audioSource.audioSessionId, audioSink.audioSessionId)

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
        if (isNoiseSuppressorSupported) {
            try {
                noiseSuppressor?.enabled = config.noiseSuppressionEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Error updating NoiseSuppressor", e)
            }
        }
        
        updateEqualizerBands()
        
        if (isDynamicsProcessingSupported) {
            try {
                dynamicsProcessing?.enabled = config.dynamicsProcessingEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Error updating DynamicsProcessing", e)
            }
        }
    }

    private fun setupAudioEffects(inputSessionId: Int, outputSessionId: Int) {
        setupNoiseSuppressor(inputSessionId)
        setupEqualizer(outputSessionId)
        setupDynamicsProcessing(outputSessionId)
    }

    private fun setupNoiseSuppressor(audioSessionId: Int) {
        if (audioSessionId == 0) {
            isNoiseSuppressorSupported = false
            events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(false))
            return
        }

        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor?.release()
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = currentConfig.noiseSuppressionEnabled
                isNoiseSuppressorSupported = true
                events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(true))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create NoiseSuppressor", e)
                isNoiseSuppressorSupported = false
                events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(false))
            }
        } else {
             Log.w(TAG, "NoiseSuppressor is not available on this device.")
             isNoiseSuppressorSupported = false
             events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(false))
        }
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

    private fun setupDynamicsProcessing(audioSessionId: Int) {
        try {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                1,
                false,
                0,
                false,
                0,
                false,
                0,
                true   
            )

            val config = builder.build()
            val limiterConfig = config.getLimiterByChannelIndex(0)
            limiterConfig.isEnabled = true
            limiterConfig.threshold = LIMITER_THRESHOLD
            limiterConfig.attackTime = LIMITER_ATTACK_TIME
            limiterConfig.releaseTime = LIMITER_RELEASE_TIME
            limiterConfig.ratio = LIMITER_RATIO
            limiterConfig.postGain = LIMITER_POST_GAIN

            dynamicsProcessing?.release()
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config)
            dynamicsProcessing?.enabled = currentConfig.dynamicsProcessingEnabled
            isDynamicsProcessingSupported = true
            events.trySend(AudioEngineEvent.DynamicsProcessingAvailability(true))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DynamicsProcessing", e)
            isDynamicsProcessingSupported = false
            events.trySend(AudioEngineEvent.DynamicsProcessingAvailability(false))
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
        noiseSuppressor?.release()
        noiseSuppressor = null
        equalizer?.release()
        equalizer = null
        dynamicsProcessing?.release()
        dynamicsProcessing = null
        isNoiseSuppressorSupported = false
        isDynamicsProcessingSupported = false
    }

    companion object {
        private const val TAG = "DefaultAudioProcessor"
        private const val LIMITER_THRESHOLD = -10.0f
        private const val LIMITER_ATTACK_TIME = 1.0f
        private const val LIMITER_RELEASE_TIME = 60.0f
        private const val LIMITER_RATIO = 10.0f
        private const val LIMITER_POST_GAIN = 0.0f
    }
}
