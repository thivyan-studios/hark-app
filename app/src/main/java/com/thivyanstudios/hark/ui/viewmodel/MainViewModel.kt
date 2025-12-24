package com.thivyanstudios.hark.ui.viewmodel

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.ui.MainUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val audioServiceManager: AudioServiceManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _snackbarChannel = Channel<String>()
    val snackbarEvents = _snackbarChannel.receiveAsFlow()

    val uiState: StateFlow<MainUiState> = audioServiceManager.service
        .flatMapLatest { service ->
            if (service != null) {
                combine(
                    service.isStreaming,
                    service.hearingAidConnected,
                    userPreferencesRepository.userPreferencesFlow
                ) { isStreaming, hearingAidConnected, prefs ->
                    MainUiState(
                        isStreaming = isStreaming,
                        hearingAidConnected = hearingAidConnected,
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn
                    )
                }
            } else {
                userPreferencesRepository.userPreferencesFlow.map { prefs ->
                    MainUiState(
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState()
        )

    @RequiresPermission(allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT])
    fun toggleStreaming(connectHearingAidMessage: String) {
        val service = audioServiceManager.service.value
        if (service != null) {
            if (service.isStreaming.value) {
                service.stopStreaming()
            } else {
                if (service.hearingAidConnected.value) {
                    service.startStreaming()
                } else {
                    viewModelScope.launch {
                        _snackbarChannel.send(connectHearingAidMessage)
                    }
                }
            }
        }
    }

    fun showPermissionsRequiredMessage(message: String) {
        viewModelScope.launch {
            _snackbarChannel.send(message)
        }
    }
}
