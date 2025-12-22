package com.thivyanstudios.hark.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.BuildConfig
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@SuppressLint("MissingPermission")
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    application: Application
) : ViewModel() {

    val versionName: StateFlow<String> = MutableStateFlow(
        try {
            val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            val currentVersionName = packageInfo.versionName
            val buildStatus = BuildConfig.BUILD_STATUS
            application.getString(
                R.string.version_text,
                buildStatus,
                currentVersionName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            application.getString(R.string.version_not_found)
        }
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

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

    val noiseSuppressionEnabled: StateFlow<Boolean> = userPreferencesRepository.noiseSuppressionEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setNoiseSuppressionEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNoiseSuppressionEnabled(isEnabled)
        }
    }
}
