package com.thivyanstudios.hark.ui.viewmodel

import com.thivyanstudios.hark.data.model.SherpaModel

data class SettingsUiState(
    val versionName: String = "",
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val enableBluetoothHeadsetSupport: Boolean = false,
    val microphoneGain: Float = 0f,
    val noiseSuppressionEnabled: Boolean = false,
    val dynamicsProcessingEnabled: Boolean = false,
    val trebleBoostEnabled: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val transcriptModeEnabled: Boolean = false,
    
    // STT Settings
    val whisperThreads: Int = 4,
    val whisperLanguage: String = "en",
    val whisperTranslate: Boolean = false,
    val silenceThreshold: Float = 0.002f,
    val transcriptionFontSize: Float = 22f,
    val selectedModelId: String = "zipformer-en-streaming",
    val availableModels: List<SherpaModel> = emptyList(),
    val downloadedModelIds: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    
    // Feature Support
    val isNoiseSuppressionSupported: Boolean = true,
    val isDynamicsProcessingSupported: Boolean = true,
    val isDeveloperOptionsEnabled: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false
)
