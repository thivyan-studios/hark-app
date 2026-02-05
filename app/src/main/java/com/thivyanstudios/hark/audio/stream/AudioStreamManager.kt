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
import com.thivyanstudios.hark.util.Constants
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AudioStreamManager(private val audioProcessor: AudioProcessor) {

    private var streamingExecutor: ExecutorService? = null
    private val isRunning = AtomicBoolean(false)

    private var activeAudioSource: AudioSource? = null
    private var activeAudioSink: AudioSink? = null
    private val lock = Any()

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
        if (!isRunning.getAndSet(false)) {
            Log.d(TAG, "stop() called but not running")
            return
        }
        Log.d(TAG, "Stopping audio stream manager")

        synchronized(lock) {
            try {
                activeAudioSource?.stop()
                activeAudioSource?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing activeAudioSource in stop()", e)
            }
            try {
                activeAudioSink?.stop()
                activeAudioSink?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing activeAudioSink in stop()", e)
            }
            activeAudioSource = null
            activeAudioSink = null
        }

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
        val bufferSize = AudioRecord.getMinBufferSize(
            Constants.Audio.SAMPLE_RATE, 
            channelConfig, 
            audioFormat
        ) * Constants.Audio.BUFFER_SIZE_MULTIPLIER

        var audioTrack: AudioTrack? = null
        var audioRecord: AudioRecord? = null

        var audioSource: AudioSource? = null
        var audioSink: AudioSink? = null

        try {
            // Initialize underlying hardware first
            audioTrack = createAudioTrack(audioFormat, bufferSize)
            audioRecord = createAudioRecord(channelConfig, audioFormat, bufferSize)
            audioSource = AndroidAudioSource(audioRecord)
            audioSink = AndroidAudioSink(audioTrack)

            synchronized(lock) {
                if (!isRunning.get()) {
                    cleanupResources(audioSource, audioSink, audioRecord, audioTrack)
                    return
                }
                activeAudioSource = audioSource
                activeAudioSink = audioSink
            }

            audioSource.start()
            audioSink.play()

            audioProcessor.process(audioSource, audioSink, config) { 
                isRunning.get() && !Thread.currentThread().isInterrupted 
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception in streaming loop", e)
        } finally {
            synchronized(lock) {
                if (activeAudioSource == audioSource) activeAudioSource = null
                if (activeAudioSink == audioSink) activeAudioSink = null
            }
            cleanupResources(audioSource, audioSink, audioRecord, audioTrack)
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
                    .setSampleRate(Constants.Audio.SAMPLE_RATE)
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
        return AudioRecord(
            MediaRecorder.AudioSource.MIC, 
            Constants.Audio.SAMPLE_RATE, 
            channelConfig, 
            audioFormat, 
            bufferSize
        )
    }

    private fun cleanupResources(
        audioSource: AudioSource?, 
        audioSink: AudioSink?,
        audioRecord: AudioRecord?,
        audioTrack: AudioTrack?
    ) {
        Log.d(TAG, "Cleaning up streaming resources.")

        // 1. Release high-level wrappers
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

        // 2. Explicitly release underlying hardware resources if they were created but not wrapped or released by wrappers
        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
        try {
            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack.stop()
            }
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }

    companion object {
        private const val TAG = "AudioStreamManager"
    }
}
