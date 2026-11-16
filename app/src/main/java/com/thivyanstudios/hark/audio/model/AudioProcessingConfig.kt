package com.thivyanstudios.hark.audio.model

data class AudioProcessingConfig(
    val microphoneGain: Float = 1.0f,
    val ambientGain: Float = 0.0f,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = true, // safety limiter on unless the user turns it off
    val trebleBoostEnabled: Boolean = false,
    val transcriptModeEnabled: Boolean = false,
)
