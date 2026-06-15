package com.thivyanstudios.hark.data.model

import com.thivyanstudios.hark.util.Constants

data class UserPreferences(
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val enableBluetoothHeadsetSupport: Boolean = false,
    val microphoneGain: Float = Constants.Preferences.DEFAULT_GAIN,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val preferExternalMic: Boolean = false,
    // Equalizer bands (-12 to +12 dB)
    val eqBand1Gain: Float = 0f,
    val eqBand2Gain: Float = 0f,
    val eqBand3Gain: Float = 0f,
    val eqBand4Gain: Float = 0f,
    val eqBand5Gain: Float = 0f
)
