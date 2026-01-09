package com.thivyanstudios.hark.audio.model

sealed class AudioEngineEvent {
    data class NoiseSuppressorAvailability(val isAvailable: Boolean) : AudioEngineEvent()
    data class DynamicsProcessingAvailability(val isAvailable: Boolean) : AudioEngineEvent()
    
    // Kept for backward compatibility if needed, but preferred to use the data classes above
    object NoiseSuppressorNotAvailable : AudioEngineEvent()
    object DynamicsProcessingNotAvailable : AudioEngineEvent()
}
