package com.thivyanstudios.hark.ui

data class MainUiState(
    val isStreaming: Boolean = false,
    val hearingAidConnected: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val transcriptModeEnabled: Boolean = false,
    val transcription: String = "",
    val audioLevel: Float = 0f, // Normalized 0.0 to 1.0
    val isLoading: Boolean = true,
    val isModelAvailable: Boolean = false,
    val history: List<com.thivyanstudios.hark.data.local.TranscriptionEntity> = emptyList(),
    val shouldShowBatteryOptimizationPrompt: Boolean = false,
    val arePermissionsHandled: Boolean = false
)
