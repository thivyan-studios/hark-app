package com.thivyanstudios.hark.ui.viewmodel

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.HeadphoneDetector
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.ui.MainUiState
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.util.Constants
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val audioServiceManager: AudioServiceManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val audioEngine: AudioEngine,
    private val headphoneDetector: HeadphoneDetector
) : ViewModel() {

    private val _snackbarChannel = Channel<String>()
    val snackbarEvents = _snackbarChannel.receiveAsFlow()

    init {
        headphoneDetector.startMonitoring()

        audioEngine.errorEvents
            .onEach { message ->
                HarkLog.e("MainViewModel", "AudioEngine error: $message")
                _snackbarChannel.send(message)
            }
            .launchIn(viewModelScope)
    }

    val uiState: StateFlow<MainUiState> = audioServiceManager.service
        .flatMapLatest { service ->
            if (service != null) {
                combine(
                    service.isStreaming,
                    service.hearingAidConnected,
                    service.audioLevel,
                    service.inputLevel,
                    service.outputLevel,
                    userPreferencesRepository.userPreferencesFlow,
                    headphoneDetector.isWiredConnected,
                    headphoneDetector.isBluetoothOnly
                ) { args ->
                    val isStreaming = args[0] as Boolean
                    val hearingAidConnected = args[1] as Boolean
                    val audioLevel = args[2] as Float
                    val inputLevel = args[3] as Float
                    val outputLevel = args[4] as Float
                    val prefs = args[5] as UserPreferences
                    val wired = args[6] as Boolean
                    val btOnly = args[7] as Boolean

                    MainUiState(
                        isStreaming = isStreaming,
                        hearingAidConnected = hearingAidConnected,
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn,
                        bypassBluetoothChecks = prefs.bypassBluetoothChecks,
                        audioLevel = audioLevel,
                        inputLevel = inputLevel,
                        outputLevel = outputLevel,
                        isServiceConnected = true,
                        isLoading = false,
                        isWiredHeadphoneConnected = wired,
                        isBluetoothOnly = btOnly
                    )
                }
            } else {
                combine(
                    userPreferencesRepository.userPreferencesFlow,
                    headphoneDetector.isWiredConnected,
                    headphoneDetector.isBluetoothOnly
                ) { prefs, wired, btOnly ->
                    MainUiState(
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn,
                        bypassBluetoothChecks = prefs.bypassBluetoothChecks,
                        isLoading = false,
                        isWiredHeadphoneConnected = wired,
                        isBluetoothOnly = btOnly
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.Preferences.TIMEOUT_MILLIS),
            initialValue = MainUiState(isLoading = true)
        )

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    fun toggleStreaming(connectHearingAidMessage: String) {
        val service = audioServiceManager.service.value
        if (service != null) {
            if (service.isStreaming.value) {
                HarkLog.i("MainViewModel", "Requesting to stop streaming")
                service.stopStreaming()
            } else {
                val shouldBypass = uiState.value.bypassBluetoothChecks

                if (shouldBypass || service.hearingAidConnected.value) {
                    HarkLog.i("MainViewModel", "Requesting to start streaming (bypass=$shouldBypass)")
                    service.startStreaming()
                } else {
                    HarkLog.w("MainViewModel", "Streaming requested but hearing aid not connected")
                    viewModelScope.launch {
                        _snackbarChannel.send(connectHearingAidMessage)
                    }
                }
            }
        } else {
            HarkLog.e("MainViewModel", "Toggle streaming failed: Service is null")
        }
    }

    fun showPermissionsRequiredMessage(message: String) {
        viewModelScope.launch {
            _snackbarChannel.send(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        headphoneDetector.stopMonitoring()
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.launchIn(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            this@launchIn.collect {}
        }
    }
}
