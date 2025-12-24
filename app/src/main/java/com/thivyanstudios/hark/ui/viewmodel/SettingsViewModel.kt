package com.thivyanstudios.hark.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.BuildConfig
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.service.AudioServiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@SuppressLint("MissingPermission")
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val audioServiceManager: AudioServiceManager,
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

    val hapticFeedbackEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.hapticFeedbackEnabled }
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

    val keepScreenOn: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.keepScreenOn }
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

    val disableHearingAidPriority: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.disableHearingAidPriority }
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

    val microphoneGain: StateFlow<Float> = userPreferencesRepository.userPreferencesFlow
        .map { it.microphoneGain }
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

    val noiseSuppressionEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.noiseSuppressionEnabled }
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
    
    val dynamicsProcessingEnabled: StateFlow<Boolean> = userPreferencesRepository.userPreferencesFlow
        .map { it.dynamicsProcessingEnabled }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )
        
    fun setDynamicsProcessingEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDynamicsProcessingEnabled(isEnabled)
        }
    }

    val equalizerBands: StateFlow<List<Float>> = userPreferencesRepository.userPreferencesFlow
        .map { it.equalizerBands }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        )

    fun setEqualizerBand(index: Int, gain: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setEqualizerBand(index, gain)
        }
    }

    fun toggleTestAudio() {
        val service = audioServiceManager.service.value
        if (service?.isTestMode?.value == true) {
            service?.stopStreaming()
        } else {
            if (service?.isStreaming?.value == true) {
                service?.stopStreaming()
            }
            service?.startTestStreaming()
        }
    }
}
