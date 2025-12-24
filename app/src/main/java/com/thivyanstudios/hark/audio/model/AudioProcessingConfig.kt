package com.thivyanstudios.hark.audio.model

data class AudioProcessingConfig(
    val microphoneGain: Float = 1.0f,
    val noiseSuppressionEnabled: Boolean = false,
    val equalizerBands: List<Float> = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
    val dynamicsProcessingEnabled: Boolean = false
)
