package com.thivyanstudios.hark.viewmodel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val bluetoothAdapter: BluetoothAdapter?
) : ViewModel() {

    val hapticFeedbackEnabled: StateFlow<Boolean> = userPreferencesRepository.hapticFeedbackEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setHapticFeedbackEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHapticFeedbackEnabled(isEnabled)
        }
    }

    val isDarkMode: StateFlow<Boolean> = userPreferencesRepository.isDarkMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun setIsDarkMode(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setIsDarkMode(isEnabled)
        }
    }

    val keepScreenOn: StateFlow<Boolean> = userPreferencesRepository.keepScreenOn
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setKeepScreenOn(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepScreenOn(isEnabled)
        }
    }

    val disableHearingAidPriority: StateFlow<Boolean> = userPreferencesRepository.disableHearingAidPriority
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setDisableHearingAidPriority(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableHearingAidPriority(isEnabled)
        }
    }

    val microphoneGain: StateFlow<Float> = userPreferencesRepository.microphoneGain
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0.0f
        )

    fun setMicrophoneGain(gain: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setMicrophoneGain(gain)
        }
    }

    private val _bluetoothDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bluetoothDevices: StateFlow<List<BluetoothDevice>> = _bluetoothDevices.asStateFlow()

    init {
        getBluetoothDevices()
    }

    private fun getBluetoothDevices() {
        bluetoothAdapter?.let {
            _bluetoothDevices.value = it.bondedDevices.toList()
        }
    }
}