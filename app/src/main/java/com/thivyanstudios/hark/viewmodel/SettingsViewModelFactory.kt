package com.thivyanstudios.hark.viewmodel

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.thivyanstudios.hark.data.UserPreferencesRepository

class SettingsViewModelFactory(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val bluetoothAdapter: BluetoothAdapter?
    ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(userPreferencesRepository, bluetoothAdapter) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}