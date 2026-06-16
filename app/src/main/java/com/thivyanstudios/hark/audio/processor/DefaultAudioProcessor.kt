package com.thivyanstudios.hark.audio.processor

import android.media.AudioRecord
import android.media.AudioTrack
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.stream.AudioSink
import com.thivyanstudios.hark.audio.stream.AudioSource
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

/**
 * Java-based audio processor: AudioRecord -> gain/EQ -> AudioTrack
 */
class DefaultAudioProcessor(private val events: Channel<AudioEngineEvent>) : AudioProcessor {

    @Volatile
    private var currentConfig = AudioProcessingConfig()

    private var processingBuffer: FloatArray? = null

    private val _inputLevel = AtomicReference(0f)
    private val _outputLevel = AtomicReference(0f)

    fun getInputLevel(): Float = _inputLevel.get()
    fun getOutputLevel(): Float = _outputLevel.get()

    override fun process(
        audioSource: AudioSource,
        audioSink: AudioSink,
        config: AudioProcessingConfig,
        isRunning: () -> Boolean
    ) {
        currentConfig = config
        setupAudioEffects()

        val bufferSize = audioSink.bufferSizeInFrames

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
        events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(true))
        events.trySend(AudioEngineEvent.DynamicsProcessingAvailability(true))
    }

    private fun applyGain(buffer: FloatArray, size: Int) {
        val gain = currentConfig.microphoneGain
        val ambientGain = currentConfig.ambientGain

        var sumInSq = 0f
        var sumOutSq = 0f
        for (i in 0 until size) {
            val raw = buffer[i]
            sumInSq += raw * raw
            val mixed = (raw * gain + raw * ambientGain).coerceIn(-1.0f, 1.0f)
            buffer[i] = mixed
            sumOutSq += mixed * mixed
        }
        val inRms = sqrt(sumInSq / size)
        val outRms = sqrt(sumOutSq / size)
        _inputLevel.set(_inputLevel.get() * 0.85f + inRms * 0.15f)
        _outputLevel.set(_outputLevel.get() * 0.85f + outRms * 0.15f)
    }

    override fun release() {
        processingBuffer = null
        _inputLevel.set(0f)
        _outputLevel.set(0f)
    }

    companion object {
        private const val TAG = "DefaultAudioProcessor"
    }
}
