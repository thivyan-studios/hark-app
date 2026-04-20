package com.thivyanstudios.hark.ui.viewmodel

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.ui.MainUiState
import com.thivyanstudios.hark.ui.SoundEvent
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
import kotlinx.coroutines.flow.map
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
    private val audioEngine: AudioEngine
) : ViewModel() {

    private val _snackbarChannel = Channel<String>()
    val snackbarEvents = _snackbarChannel.receiveAsFlow()

    init {
        // QC: Listen for errors from the audio engine and bridge them to snackbars
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
                    service.transcription,
                    service.activeSoundEvents,
                    service.audioLevel,
                    userPreferencesRepository.userPreferencesFlow
                ) { args ->
                    val isStreaming = args[0] as Boolean
                    val hearingAidConnected = args[1] as Boolean
                    val transcription = args[2] as String
                    @Suppress("UNCHECKED_CAST")
                    val activeSoundEvents = args[3] as List<SoundEvent>
                    val audioLevel = args[4] as Float
                    val prefs = args[5] as UserPreferences
                    
                    MainUiState(
                        isStreaming = isStreaming,
                        hearingAidConnected = hearingAidConnected,
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn,
                        bypassBluetoothChecks = prefs.bypassBluetoothChecks,
                        transcriptModeEnabled = prefs.transcriptModeEnabled,
                        transcription = transcription,
                        activeSoundEvents = activeSoundEvents,
                        audioLevel = audioLevel
                    )
                }
            } else {
                userPreferencesRepository.userPreferencesFlow.map { prefs ->
                    MainUiState(
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn,
                        bypassBluetoothChecks = prefs.bypassBluetoothChecks,
                        transcriptModeEnabled = prefs.transcriptModeEnabled
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.Preferences.TIMEOUT_MILLIS),
            initialValue = MainUiState()
        )

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    fun toggleStreaming(connectHearingAidMessage: String) {
        val service = audioServiceManager.service.value
        if (service != null) {
            if (service.isStreaming.value) {
                HarkLog.i("MainViewModel", "Requesting to stop streaming")
                service.stopStreaming()
            } else {
                // Check if we should bypass bluetooth connection checks
                val shouldBypass = uiState.value.bypassBluetoothChecks || uiState.value.transcriptModeEnabled
                
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
        HarkLog.w("MainViewModel", "Showing permissions required message")
        viewModelScope.launch {
            _snackbarChannel.send(message)
        }
    }

    fun clearTranscription() {
        audioServiceManager.service.value?.clearTranscription()
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.launchIn(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            this@launchIn.collect {}
        }
    }
}
