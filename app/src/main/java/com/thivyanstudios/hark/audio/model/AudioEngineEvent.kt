package com.thivyanstudios.hark.audio.model

/**
 * Events sent from the Audio Engine to the UI/Service layers.
 */
sealed class AudioEngineEvent {
    /** Indicates if Noise Suppression is supported and its current state. */
    data class NoiseSuppressorAvailability(val isAvailable: Boolean) : AudioEngineEvent()
    
    /** Indicates if Dynamics Processing (Limiter) is supported and its current state. */
    data class DynamicsProcessingAvailability(val isAvailable: Boolean) : AudioEngineEvent()
}
