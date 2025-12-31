package com.thivyanstudios.hark.audio.stream

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.processor.AudioProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AudioStreamManager(private val audioProcessor: AudioProcessor) {

    private var streamingExecutor: ExecutorService? = null
    private val isRunning = AtomicBoolean(false)
    private var useTestGenerator = false

    @SuppressLint("MissingPermission")
    fun start(config: AudioProcessingConfig) {
        startStreaming(config, false)
    }

    fun startTest(config: AudioProcessingConfig) {
        startStreaming(config, true)
    }

    private fun startStreaming(config: AudioProcessingConfig, isTest: Boolean) {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "start() called but already running")
            // If already running, we check if we need to switch mode.
            // If we are switching from test to normal or vice versa, we should restart.
            if (useTestGenerator != isTest) {
               Log.d(TAG, "Switching streaming mode (Test Mode: $isTest)")
               // We need to stop current one first, but since we are in start(), 
               // and we set isRunning to true above, we might be in a weird state if we just call stop() which sets it to false.
               
               // Let's rely on the caller to stop first if switching modes is needed.
               // But wait, the user's issue is that "Test Settings" doesn't stop. 
               // The Toggle in ViewModel calls stopStreaming() if it believes it is running.
               // The logic in AudioStreamingService.stopStreaming calls AudioEngine.stop() which calls AudioStreamManager.stop().
               
               // If startStreaming is called while running, we generally just return.
               return
            }
            return
        }

        useTestGenerator = isTest
        Log.d(TAG, "Starting audio stream manager (Test Mode: $isTest)")

        val executor = Executors.newSingleThreadExecutor()
        streamingExecutor = executor

        executor.execute {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runStreamingLoop(config)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) {
            Log.d(TAG, "stop() called but not running")
            return
        }
        Log.d(TAG, "Stopping audio stream manager")

        streamingExecutor?.shutdown()
        try {
            if (streamingExecutor?.awaitTermination(1, TimeUnit.SECONDS) == false) {
                 streamingExecutor?.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for executor termination", e)
             streamingExecutor?.shutdownNow()
        }
    }

    fun updateConfig(config: AudioProcessingConfig) {
        audioProcessor.updateConfig(config)
    }

    @SuppressLint("MissingPermission")
    private fun runStreamingLoop(config: AudioProcessingConfig) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat) * BUFFER_SIZE_MULTIPLIER

        val audioTrack = createAudioTrack(audioFormat, bufferSize)
        var audioRecord: AudioRecord? = null

        var audioSource: AudioSource? = null
        var audioSink: AudioSink? = null

        try {
            if (useTestGenerator) {
                audioSource = PinkNoiseAudioSource()
            } else {
                audioRecord = createAudioRecord(channelConfig, audioFormat, bufferSize)
                audioSource = AndroidAudioSource(audioRecord)
            }
            
            // Create wrappers
            audioSink = AndroidAudioSink(audioTrack)

            audioSource.start()
            audioSink.play()

            // Pass a lambda that checks the AtomicBoolean isRunning
            // IMPORTANT: The loop inside processor needs to check this lambda frequently.
            audioProcessor.process(audioSource, audioSink, config) { 
                isRunning.get() && !Thread.currentThread().isInterrupted 
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception in streaming loop", e)
        } finally {
            cleanupResources(audioSource, audioSink)
        }
    }

    private fun createAudioTrack(audioFormat: Int, bufferSize: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(channelConfig: Int, audioFormat: Int, bufferSize: Int): AudioRecord {
        return AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, channelConfig, audioFormat, bufferSize)
    }

    private fun cleanupResources(audioSource: AudioSource?, audioSink: AudioSink?) {
        Log.d(TAG, "Cleaning up streaming resources.")

        try {
            audioSource?.stop()
            audioSource?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioSource", e)
        }
        try {
            audioSink?.stop()
            audioSink?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioSink", e)
        }
    }

    companion object {
        private const val TAG = "AudioStreamManager"
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }
}
