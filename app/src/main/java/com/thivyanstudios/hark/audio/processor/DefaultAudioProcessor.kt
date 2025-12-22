package com.thivyanstudios.hark.audio.processor

import android.media.AudioRecord
import android.media.AudioTrack
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import kotlinx.coroutines.channels.Channel

class DefaultAudioProcessor(private val events: Channel<AudioEngineEvent>) : AudioProcessor {

    private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile
    private var currentConfig = AudioProcessingConfig()

    override fun process(
        audioRecord: AudioRecord,
        audioTrack: AudioTrack,
        config: AudioProcessingConfig,
        isRunning: () -> Boolean
    ) {
        currentConfig = config
        setupNoiseSuppressor(audioRecord.audioSessionId)

        val bufferSize = audioTrack.bufferSizeInFrames
        val buffer = FloatArray(bufferSize)

        while (isRunning()) {
            val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (read > 0) {
                applyGain(buffer, read)
                audioTrack.write(buffer, 0, read, AudioTrack.WRITE_BLOCKING)
            }
        }
    }

    override fun updateConfig(config: AudioProcessingConfig) {
        currentConfig = config
        noiseSuppressor?.enabled = config.noiseSuppressionEnabled
    }

    private fun setupNoiseSuppressor(audioSessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId)
            noiseSuppressor?.enabled = currentConfig.noiseSuppressionEnabled
        } else {
            Log.w(TAG, "NoiseSuppressor is not available on this device.")
            events.trySend(AudioEngineEvent.NoiseSuppressorNotAvailable)
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
    }

    companion object {
        private const val TAG = "DefaultAudioProcessor"
    }
}
