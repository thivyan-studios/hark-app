package com.thivyanstudios.hark.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class AudioEngine @Inject constructor() {

    private var streamingExecutor: ExecutorService? = null
    private val isRunning = AtomicBoolean(false)
    @Volatile
    private var microphoneGain = 1.0f
    @Volatile
    private var noiseSuppressionEnabled = false
    private var noiseSuppressor: NoiseSuppressor? = null

    // Channel to send one-time events to the Service/UI
    val events = Channel<AudioEngineEvent>(Channel.BUFFERED)

    fun setMicrophoneGain(gain: Float) {
        microphoneGain = gain
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        noiseSuppressionEnabled = enabled
        // If we already have a suppressor instance, update its state dynamically
        noiseSuppressor?.enabled = enabled
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "start() called but already running")
            return
        }

        Log.d(TAG, "Starting audio engine")

        val executor = Executors.newCachedThreadPool()
        streamingExecutor = executor

        executor.execute {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat) * BUFFER_SIZE_MULTIPLIER

            val audioPlayBack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                .build()

            var audioRecord: AudioRecord? = null

            try {
                audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, channelConfig, audioFormat, bufferSize)

                if (NoiseSuppressor.isAvailable()) {
                     noiseSuppressor = NoiseSuppressor.create(audioRecord.audioSessionId)
                     noiseSuppressor?.enabled = noiseSuppressionEnabled
                } else {
                    Log.w(TAG, "NoiseSuppressor is not available on this device.")
                    events.trySend(AudioEngineEvent.NoiseSuppressorNotAvailable)
                }

                audioRecord.startRecording()
                audioPlayBack.play()

                while (isRunning.get()) {
                    val buffer = FloatArray(bufferSize / 4)
                    val read = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        val currentGain = microphoneGain
                        for (i in 0 until read) {
                            buffer[i] *= currentGain
                        }
                        audioPlayBack.write(buffer, 0, read, AudioTrack.WRITE_BLOCKING)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in streaming loop", e)
            } finally {
                Log.d(TAG, "Cleaning up streaming resources.")
                
                noiseSuppressor?.release()
                noiseSuppressor = null

                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing AudioRecord", e)
                }
                try {
                    audioPlayBack.stop()
                    audioPlayBack.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing AudioTrack", e)
                }
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.d(TAG, "Stopping audio engine")

        streamingExecutor?.shutdownNow()
        try {
            if (streamingExecutor?.awaitTermination(1, TimeUnit.SECONDS) == false) {
                Log.e(TAG, "Streaming executor did not terminate in time.")
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for executor termination", e)
        }
    }

    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }
}

sealed class AudioEngineEvent {
    object NoiseSuppressorNotAvailable : AudioEngineEvent()
}
