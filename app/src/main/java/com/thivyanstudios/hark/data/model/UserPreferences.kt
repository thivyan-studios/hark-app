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
    val transcriptModeEnabled: Boolean = false,
    val whisperThreads: Int = 4,
    val whisperLanguage: String = "en",
    val whisperTranslate: Boolean = false,
    val silenceThreshold: Float = 0.005f,
    val transcriptionFontSize: Float = 22f,
    val selectedModelId: String = "ggml-base-q8_0"
)
