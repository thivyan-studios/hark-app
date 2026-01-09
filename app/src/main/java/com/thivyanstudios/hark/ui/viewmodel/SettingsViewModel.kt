package com.thivyanstudios.hark.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.BuildConfig
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@SuppressLint("MissingPermission")
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val audioServiceManager: AudioServiceManager,
    private val audioEngine: AudioEngine,
    private val application: Application
) : ViewModel() {

    private val _versionName = MutableStateFlow("")
    private val _isNoiseSuppressionSupported = MutableStateFlow(true)
    private val _isDynamicsProcessingSupported = MutableStateFlow(true)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _versionName.value = try {
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
        }

        // Listen for audio engine events to update feature support
        audioEngine.events.receiveAsFlow()
            .onEach { event ->
                when (event) {
                    is AudioEngineEvent.NoiseSuppressorAvailability -> {
                        _isNoiseSuppressionSupported.value = event.isAvailable
                    }
                    is AudioEngineEvent.DynamicsProcessingAvailability -> {
                        _isDynamicsProcessingSupported.value = event.isAvailable
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.userPreferencesFlow,
        _versionName,
        _isNoiseSuppressionSupported,
        _isDynamicsProcessingSupported
    ) { prefs, version, nsSupported, dpSupported ->
        SettingsUiState(
            versionName = version,
            hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
            keepScreenOn = prefs.keepScreenOn,
            disableHearingAidPriority = prefs.disableHearingAidPriority,
            microphoneGain = prefs.microphoneGain,
            noiseSuppressionEnabled = prefs.noiseSuppressionEnabled,
            dynamicsProcessingEnabled = prefs.dynamicsProcessingEnabled,
            equalizerBands = prefs.equalizerBands,
            isNoiseSuppressionSupported = nsSupported,
            isDynamicsProcessingSupported = dpSupported
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
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
