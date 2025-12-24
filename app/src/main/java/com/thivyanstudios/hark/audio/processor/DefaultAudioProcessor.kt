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
        noiseSuppressor?.enabled = config.noiseSuppressionEnabled
        updateEqualizerBands()
        dynamicsProcessing?.enabled = config.dynamicsProcessingEnabled
    }

    private fun setupAudioEffects(inputSessionId: Int, outputSessionId: Int) {
        setupNoiseSuppressor(inputSessionId)
        setupEqualizer(outputSessionId)
        setupDynamicsProcessing(outputSessionId)
    }

    private fun setupNoiseSuppressor(audioSessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            try {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)
                noiseSuppressor?.enabled = currentConfig.noiseSuppressionEnabled
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create NoiseSuppressor", e)
                events.trySend(AudioEngineEvent.NoiseSuppressorNotAvailable)
            }
        } else {
            Log.w(TAG, "NoiseSuppressor is not available on this device.")
            events.trySend(AudioEngineEvent.NoiseSuppressorNotAvailable)
        }
    }

    private fun setupEqualizer(audioSessionId: Int) {
        try {
            equalizer = Equalizer(0, audioSessionId)
            equalizer?.enabled = true
            updateEqualizerBands()
        } catch (e: Exception) {
             Log.e(TAG, "Failed to create Equalizer", e)
        }
    }

    private fun setupDynamicsProcessing(audioSessionId: Int) {
        try {
            // DynamicsProcessing.Config.Builder requires precise arguments
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                1, // channel count
                false, // preEqInUse
                0,     // preEqBandCount
                false, // mbcInUse
                0,     // mbcBandCount
                false, // postEqInUse
                0,     // postEqBandCount
                true   // limiterInUse
            )

            val config = builder.build()

            // Configure Limiter
            val limiterConfig = config.getLimiterByChannelIndex(0)
            limiterConfig.isEnabled = true
            limiterConfig.threshold = -10.0f
            limiterConfig.attackTime = 1.0f
            limiterConfig.releaseTime = 60.0f
            limiterConfig.ratio = 10.0f
            limiterConfig.postGain = 0.0f

            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config)
            dynamicsProcessing?.enabled = currentConfig.dynamicsProcessingEnabled

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create DynamicsProcessing", e)
            events.trySend(AudioEngineEvent.DynamicsProcessingNotAvailable)
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
            // Convert dB to millibels (mB) used by Equalizer API
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
    }

    companion object {
        private const val TAG = "DefaultAudioProcessor"
    }
}