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

    @SuppressLint("MissingPermission")
    fun start(config: AudioProcessingConfig) {
        if (isRunning.getAndSet(true)) {
            Log.d(TAG, "start() called but already running")
            return
        }

        Log.d(TAG, "Starting audio stream manager")

        val executor = Executors.newSingleThreadExecutor()
        streamingExecutor = executor

        executor.execute {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runStreamingLoop(config)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.d(TAG, "Stopping audio stream manager")

        streamingExecutor?.shutdownNow()
        try {
            if (streamingExecutor?.awaitTermination(1, TimeUnit.SECONDS) == false) {
                Log.e(TAG, "Streaming executor did not terminate in time.")
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for executor termination", e)
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

        try {
            audioRecord = createAudioRecord(channelConfig, audioFormat, bufferSize)

            audioRecord.startRecording()
            audioTrack.play()

            audioProcessor.process(audioRecord, audioTrack, config) { isRunning.get() }

        } catch (e: Exception) {
            Log.e(TAG, "Exception in streaming loop", e)
        } finally {
            cleanupResources(audioRecord, audioTrack)
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

    private fun cleanupResources(audioRecord: AudioRecord?, audioTrack: AudioTrack) {
        Log.d(TAG, "Cleaning up streaming resources.")

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        try {
            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }

    companion object {
        private const val TAG = "AudioStreamManager"
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }
}
