package com.thivyanstudios.hark.ui

data class MainUiState(
    val isStreaming: Boolean = false,
    val hearingAidConnected: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val audioLevel: Float = 0f,
    val isLoading: Boolean = true,
    val isWiredHeadphoneConnected: Boolean = false,
    val isBluetoothOnly: Boolean = false
)
