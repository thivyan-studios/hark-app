package com.thivyanstudios.hark.data.model

import com.thivyanstudios.hark.util.Constants

data class UserPreferences(
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val disableHearingAidPriority: Boolean = false,
    val microphoneGain: Float = Constants.Preferences.DEFAULT_GAIN,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val whisperThreads: Int = 4,
    val whisperLanguage: String = "en",
    val whisperTranslate: Boolean = false
)
