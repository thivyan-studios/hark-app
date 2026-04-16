package com.thivyanstudios.hark.ui

data class MainUiState(
    val isStreaming: Boolean = false,
    val hearingAidConnected: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val transcription: String = "",
    val activeSoundEvents: List<SoundEvent> = emptyList()
)

data class SoundEvent(
    val label: String,
    val timestamp: Long = System.currentTimeMillis()
)
