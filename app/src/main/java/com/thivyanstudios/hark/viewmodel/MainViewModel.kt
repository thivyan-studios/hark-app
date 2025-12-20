package com.thivyanstudios.hark.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.ui.MainUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
                    userPreferencesRepository.hapticFeedbackEnabled,
                    userPreferencesRepository.isDarkMode,
                    userPreferencesRepository.keepScreenOn
                ) { isStreaming, hearingAidConnected, hapticFeedbackEnabled, isDarkMode, keepScreenOn ->
                    MainUiState(
                        isStreaming = isStreaming,
                        hearingAidConnected = hearingAidConnected,
                        hapticFeedbackEnabled = hapticFeedbackEnabled,
                        isDarkMode = isDarkMode,
                        keepScreenOn = keepScreenOn
                    )
                }
            } else {
                combine(
                    userPreferencesRepository.hapticFeedbackEnabled,
                    userPreferencesRepository.isDarkMode,
                    userPreferencesRepository.keepScreenOn
                ) { hapticFeedbackEnabled, isDarkMode, keepScreenOn ->
                    MainUiState(
                        hapticFeedbackEnabled = hapticFeedbackEnabled,
                        isDarkMode = isDarkMode,
                        keepScreenOn = keepScreenOn
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState()
        )

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
