package com.thivyanstudios.hark.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioEngine"

@Singleton
class AudioEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var isNativeLoaded = false
    private var sampleRate = 48000
    private var framesPerBurst = 192

    init {
        try {
            System.loadLibrary("hark")
            
            // Query optimal audio parameters
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000
            framesPerBurst = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 192
            
            nativeInit(sampleRate, framesPerBurst)
            isNativeLoaded = true
            
            Log.d(TAG, "Audio parameters: SampleRate=$sampleRate, FramesPerBurst=$framesPerBurst")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load native library 'hark'", e)
            isNativeLoaded = false
        }
    }

    val events = Channel<AudioEngineEvent>(Channel.BUFFERED)
    
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents = _errorEvents.asSharedFlow()

    private var currentConfig = AudioProcessingConfig()

    fun start(): Boolean {
        if (!isNativeLoaded) {
            sendError("Native library not loaded")
            return false
        }

        return try {
            // Re-sync configuration before starting
            syncConfigWithNative()
            
            val success = nativeStart()
            if (success) {
                events.trySend(AudioEngineEvent.NoiseSuppressorAvailability(true))
                events.trySend(AudioEngineEvent.DynamicsProcessingAvailability(true))
            } else {
                sendError("Failed to start Native Audio Engine")
            }
            success
        } catch (e: UnsatisfiedLinkError) {
            sendError("Native method not found: nativeStart")
            false
        }
    }

    private fun syncConfigWithNative() {
        if (!isNativeLoaded) return
        
        try {
            nativeSetMicrophoneGain(currentConfig.microphoneGain)
            nativeSetNoiseSuppressionEnabled(currentConfig.noiseSuppressionEnabled)
            nativeSetDynamicsProcessingEnabled(currentConfig.dynamicsProcessingEnabled)
            nativeSetEqualizerBands(currentConfig.equalizerBands.toFloatArray())
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to sync config with native engine", e)
        }
    }

    fun stop() {
        if (!isNativeLoaded) return
        try {
            nativeStop()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: nativeStop", e)
        }
    }

    fun destroy() {
        if (!isNativeLoaded) return
        try {
            nativeStop()
            nativeDestroy()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not found: nativeDestroy", e)
        }
    }
    
    fun sendError(message: String) {
        _errorEvents.tryEmit(message)
    }

    fun setMicrophoneGain(gain: Float) {
        if (currentConfig.microphoneGain == gain) return
        currentConfig = currentConfig.copy(microphoneGain = gain)
        if (isNativeLoaded) {
            try { nativeSetMicrophoneGain(gain) } catch (e: UnsatisfiedLinkError) {}
        }
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        if (currentConfig.noiseSuppressionEnabled == enabled) return
        currentConfig = currentConfig.copy(noiseSuppressionEnabled = enabled)
        if (isNativeLoaded) {
            try { nativeSetNoiseSuppressionEnabled(enabled) } catch (e: UnsatisfiedLinkError) {}
        }
    }
    
    fun setEqualizerBands(bands: List<Float>) {
        if (currentConfig.equalizerBands == bands) return
        currentConfig = currentConfig.copy(equalizerBands = bands)
        if (isNativeLoaded) {
            try { nativeSetEqualizerBands(bands.toFloatArray()) } catch (e: UnsatisfiedLinkError) {}
        }
    }
    
    fun setDynamicsProcessingEnabled(enabled: Boolean) {
        if (currentConfig.dynamicsProcessingEnabled == enabled) return
        currentConfig = currentConfig.copy(dynamicsProcessingEnabled = enabled)
        if (isNativeLoaded) {
            try { nativeSetDynamicsProcessingEnabled(enabled) } catch (e: UnsatisfiedLinkError) {}
        }
    }

    fun setTestMode(enabled: Boolean) {
        if (isNativeLoaded) {
            try { nativeSetTestMode(enabled) } catch (e: UnsatisfiedLinkError) {}
        }
    }

    // Native methods
    private external fun nativeInit(sampleRate: Int, framesPerBurst: Int)
    private external fun nativeDestroy()
    private external fun nativeStart(): Boolean
    private external fun nativeStop()
    private external fun nativeSetMicrophoneGain(gain: Float)
    private external fun nativeSetNoiseSuppressionEnabled(enabled: Boolean)
    private external fun nativeSetDynamicsProcessingEnabled(enabled: Boolean)
    private external fun nativeSetEqualizerBands(bands: FloatArray)
    private external fun nativeSetTestMode(enabled: Boolean)
}
