package com.thivyanstudios.hark.ui.viewmodel

import android.Manifest
import android.content.Context
import android.os.PowerManager
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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
    @ApplicationContext private val context: Context,
    private val audioServiceManager: AudioServiceManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val transcriptionRepository: com.thivyanstudios.hark.data.TranscriptionRepository,
    private val whisperModelManager: com.thivyanstudios.hark.data.WhisperModelManager,
    private val audioEngine: AudioEngine
) : ViewModel() {

    private val _snackbarChannel = Channel<String>()
    val snackbarEvents = _snackbarChannel.receiveAsFlow()

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())
    private val _arePermissionsHandled = MutableStateFlow(false)

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
            val baseFlows = if (service != null) {
                combine(
                    service.isStreaming,
                    service.hearingAidConnected,
                    service.transcription,
                    service.activeSoundEvents,
                    service.audioLevel,
                    userPreferencesRepository.userPreferencesFlow,
                    transcriptionRepository.allTranscriptions,
                    whisperModelManager.modelStoreUpdateTrigger,
                    refreshTrigger,
                    _arePermissionsHandled
                ) { args ->
                    val isStreaming = args[0] as Boolean
                    val hearingAidConnected = args[1] as Boolean
                    val transcription = args[2] as String
                    @Suppress("UNCHECKED_CAST")
                    val activeSoundEvents = args[3] as List<SoundEvent>
                    val audioLevel = args[4] as Float
                    val prefs = args[5] as UserPreferences
                    @Suppress("UNCHECKED_CAST")
                    val history = args[6] as List<com.thivyanstudios.hark.data.local.TranscriptionEntity>
                    // args[7] is modelStoreUpdateTrigger
                    // args[8] is refreshTrigger
                    val permissionsHandled = args[9] as Boolean

                    val isModelAvailable = whisperModelManager.availableModels.any { 
                        whisperModelManager.isModelDownloaded(it) 
                    }
                    
                    val isBatteryOptimizationIgnored = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .isIgnoringBatteryOptimizations(context.packageName)

                    MainUiState(
                        isStreaming = isStreaming,
                        hearingAidConnected = hearingAidConnected,
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn,
                        bypassBluetoothChecks = prefs.bypassBluetoothChecks,
                        transcriptModeEnabled = prefs.transcriptModeEnabled,
                        transcription = transcription,
                        activeSoundEvents = activeSoundEvents,
                        audioLevel = audioLevel,
                        isLoading = false,
                        isModelAvailable = isModelAvailable,
                        history = history,
                        shouldShowBatteryOptimizationPrompt = permissionsHandled && !prefs.batteryOptimizationPromptShown && !isBatteryOptimizationIgnored,
                        arePermissionsHandled = permissionsHandled
                    )
                }
            } else {
                combine(
                    userPreferencesRepository.userPreferencesFlow,
                    transcriptionRepository.allTranscriptions,
                    whisperModelManager.modelStoreUpdateTrigger,
                    refreshTrigger,
                    _arePermissionsHandled
                ) { args ->
                    val prefs = args[0] as UserPreferences
                    @Suppress("UNCHECKED_CAST")
                    val history = args[1] as List<com.thivyanstudios.hark.data.local.TranscriptionEntity>
                    // args[2] is modelStoreUpdateTrigger
                    // args[3] is refreshTrigger
                    val permissionsHandled = args[4] as Boolean

                    val isModelAvailable = whisperModelManager.availableModels.any { 
                        whisperModelManager.isModelDownloaded(it) 
                    }

                    val isBatteryOptimizationIgnored = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                        .isIgnoringBatteryOptimizations(context.packageName)
                    
                    MainUiState(
                        hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                        keepScreenOn = prefs.keepScreenOn,
                        bypassBluetoothChecks = prefs.bypassBluetoothChecks,
                        transcriptModeEnabled = prefs.transcriptModeEnabled,
                        isLoading = false,
                        isModelAvailable = isModelAvailable,
                        history = history,
                        shouldShowBatteryOptimizationPrompt = permissionsHandled && !prefs.batteryOptimizationPromptShown && !isBatteryOptimizationIgnored,
                        arePermissionsHandled = permissionsHandled
                    )
                }
            }
            baseFlows
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
        val currentText = uiState.value.transcription
        if (currentText.isNotBlank()) {
            viewModelScope.launch {
                transcriptionRepository.insert(currentText)
            }
        }
        audioServiceManager.service.value?.clearTranscription()
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            transcriptionRepository.delete(id)
        }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            transcriptionRepository.deleteAll()
        }
    }

    fun setBatteryOptimizationPromptShown(shown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBatteryOptimizationPromptShown(shown)
        }
    }

    fun setPermissionsHandled(handled: Boolean) {
        _arePermissionsHandled.value = handled
    }

    fun refreshBatteryStatus() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.launchIn(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            this@launchIn.collect {}
        }
    }
}
