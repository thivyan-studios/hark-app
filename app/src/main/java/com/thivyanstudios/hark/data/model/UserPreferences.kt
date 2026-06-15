package com.thivyanstudios.hark.data.model

import com.thivyanstudios.hark.util.Constants

data class UserPreferences(
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val enableBluetoothHeadsetSupport: Boolean = true,
    val microphoneGain: Float = Constants.Preferences.DEFAULT_GAIN,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val preferExternalMic: Boolean = false,
    val eqBandGains: List<Float> = List(10) { 0f },
    val eqLeftBandGains: List<Float> = List(10) { 0f },
    val eqRightBandGains: List<Float> = List(10) { 0f },
    val eqLinkLR: Boolean = true,
    val eqBandMode: Int = 10,
    val noiseReductionEnabled: Boolean = true,
    val hearingProtectionEnabled: Boolean = true,
    val limiterThresholdDb: Float = -10f
) {
    val eqBand1Gain: Float get() = eqBandGains.getOrElse(0) { 0f }
    val eqBand2Gain: Float get() = eqBandGains.getOrElse(1) { 0f }
    val eqBand3Gain: Float get() = eqBandGains.getOrElse(2) { 0f }
    val eqBand4Gain: Float get() = eqBandGains.getOrElse(3) { 0f }
    val eqBand5Gain: Float get() = eqBandGains.getOrElse(4) { 0f }
}