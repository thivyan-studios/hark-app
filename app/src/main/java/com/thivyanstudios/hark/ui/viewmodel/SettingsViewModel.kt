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
import com.thivyanstudios.hark.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
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

    private val _versionName: String = try {
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

    val uiState: StateFlow<SettingsUiState> = userPreferencesRepository.userPreferencesFlow
        .map { prefs ->
            SettingsUiState(
                versionName = _versionName,
                hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                keepScreenOn = prefs.keepScreenOn,
                disableHearingAidPriority = prefs.disableHearingAidPriority,
                microphoneGain = prefs.microphoneGain,
                noiseSuppressionEnabled = prefs.noiseSuppressionEnabled,
                dynamicsProcessingEnabled = prefs.dynamicsProcessingEnabled,
                equalizerBands = prefs.equalizerBands
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.Preferences.TIMEOUT_MILLIS),
            initialValue = SettingsUiState(versionName = _versionName)
        )

    fun setHapticFeedbackEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHapticFeedbackEnabled(isEnabled)
        }
    }

    fun setKeepScreenOn(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepScreenOn(isEnabled)
        }
    }

    fun setDisableHearingAidPriority(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableHearingAidPriority(isEnabled)
        }
    }

    fun setMicrophoneGain(gain: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setMicrophoneGain(gain)
        }
    }

    fun setNoiseSuppressionEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNoiseSuppressionEnabled(isEnabled)
        }
    }
    
    fun setDynamicsProcessingEnabled(isEnabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDynamicsProcessingEnabled(isEnabled)
        }
    }

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
