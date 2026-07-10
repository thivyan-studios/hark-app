package com.thivyanstudios.hark.ui.viewmodel

import com.thivyanstudios.hark.util.Constants

data class SettingsUiState(
    val versionName: String = "",
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val enableBluetoothHeadsetSupport: Boolean = false,
    val microphoneGain: Float = Constants.Preferences.DEFAULT_GAIN,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val transcriptModeEnabled: Boolean = false,
    
    // Whisper Settings
    val whisperThreads: Int = 4,
    val whisperLanguage: String = "en",
    val whisperTranslate: Boolean = false,
    val silenceThreshold: Float = 0.005f,
    val transcriptionFontSize: Float = 22f,
    val selectedModelId: String = "ggml-base-q8_0",
    val availableModels: List<com.thivyanstudios.hark.data.model.WhisperModel> = emptyList(),
    val downloadedModelIds: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    
    // Feature support flags
    val isNoiseSuppressionSupported: Boolean = true,
    val isDynamicsProcessingSupported: Boolean = true,
    val isDeveloperOptionsEnabled: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = true
)
