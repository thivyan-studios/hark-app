package com.thivyanstudios.hark.ui

import com.thivyanstudios.hark.ui.model.AudioDeviceOption

data class MainUiState(
    val isStreaming: Boolean = false,
    val hearingAidConnected: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val bypassBluetoothChecks: Boolean = false,
    val audioLevel: Float = 0f,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val isServiceConnected: Boolean = false,
    val isLoading: Boolean = true,
    val isWiredHeadphoneConnected: Boolean = false,
    val isBluetoothOnly: Boolean = false,
    val latencyMs: Float = 0f,
    val outputDeviceName: String = "",
    val inputDeviceName: String = "",
    val availableInputDevices: List<AudioDeviceOption> = emptyList(),
    val availableOutputDevices: List<AudioDeviceOption> = emptyList(),
    val selectedInputDeviceId: Int = -1,
    val selectedOutputDeviceId: Int = -1
)
