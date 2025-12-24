package com.thivyanstudios.hark.data.model

data class UserPreferences(
    val hapticFeedbackEnabled: Boolean,
    val keepScreenOn: Boolean,
    val disableHearingAidPriority: Boolean,
    val microphoneGain: Float,
    val noiseSuppressionEnabled: Boolean,
    val equalizerBands: List<Float>,
    val dynamicsProcessingEnabled: Boolean
)
