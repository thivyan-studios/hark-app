package com.thivyanstudios.hark.ui.viewmodel

import com.thivyanstudios.hark.util.Constants

data class SettingsUiState(
    val versionName: String = "",
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val disableHearingAidPriority: Boolean = false,
    val microphoneGain: Float = Constants.Preferences.DEFAULT_GAIN,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = false,
    
    // Feature support flags
    val isNoiseSuppressionSupported: Boolean = true, // Default to true until proven otherwise
    val isDynamicsProcessingSupported: Boolean = true
)
