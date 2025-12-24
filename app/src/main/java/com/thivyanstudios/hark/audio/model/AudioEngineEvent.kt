package com.thivyanstudios.hark.audio.model

sealed class AudioEngineEvent {
    object NoiseSuppressorNotAvailable : AudioEngineEvent()
    object DynamicsProcessingNotAvailable : AudioEngineEvent()
}
