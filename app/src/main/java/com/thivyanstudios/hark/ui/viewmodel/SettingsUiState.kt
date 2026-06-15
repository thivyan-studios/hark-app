package com.thivyanstudios.hark.ui.viewmodel

import com.thivyanstudios.hark.util.Constants

data class SettingsUiState(
    val versionName: String = "",
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val enableBluetoothHeadsetSupport: Boolean = false,
    val preferExternalMic: Boolean = false,
    val microphoneGain: Float = Constants.Preferences.DEFAULT_GAIN,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,

    val isNoiseSuppressionSupported: Boolean = true,
    val isDynamicsProcessingSupported: Boolean = true,
    val isDeveloperOptionsEnabled: Boolean = false
)
