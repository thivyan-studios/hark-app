package com.thivyanstudios.hark.ui.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thivyanstudios.hark.BuildConfig
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.di.IoDispatcher
import com.thivyanstudios.hark.util.HarkLog
import com.thivyanstudios.hark.util.SystemSettingsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@SuppressLint("MissingPermission")
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val audioEngine: AudioEngine,
    private val application: Application,
    private val systemSettingsProvider: SystemSettingsProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _versionName = MutableStateFlow("")
    private val _isNoiseSuppressionSupported = MutableStateFlow(true)
    private val _isDynamicsProcessingSupported = MutableStateFlow(true)
    private val _isDeveloperOptionsEnabled = MutableStateFlow(false)

    private val devSettingsObserver by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                updateDeveloperOptionsStatus()
            }
        }
    }

    init {
        viewModelScope.launch(ioDispatcher) {
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
            
            updateDeveloperOptionsStatus()
        }

        // Only register if we're not in a unit test environment
        if (Looper.myLooper() != null) {
           try {
               application.contentResolver.registerContentObserver(
                   systemSettingsProvider.developmentSettingsUri,
                   false,
                   devSettingsObserver
               )
           } catch (e: Exception) {
               HarkLog.w("SettingsViewModel", "Failed to register content observer")
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

    private fun updateDeveloperOptionsStatus() {
        val isEnabled = systemSettingsProvider.isDeveloperOptionsEnabled()
        _isDeveloperOptionsEnabled.value = isEnabled
        
        // If developer options are disabled, force the bypass check preference to false
        if (!isEnabled) {
            viewModelScope.launch(ioDispatcher) {
                userPreferencesRepository.setBypassBluetoothChecks(false)
            }
        }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        userPreferencesRepository.userPreferencesFlow,
        _versionName,
        _isNoiseSuppressionSupported,
        _isDynamicsProcessingSupported,
        _isDeveloperOptionsEnabled
    ) { prefs, version, nsSupported, dpSupported, devEnabled ->
        SettingsUiState(
            versionName = version,
            hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
            keepScreenOn = prefs.keepScreenOn,
            disableHearingAidPriority = prefs.disableHearingAidPriority,
            microphoneGain = prefs.microphoneGain,
            noiseSuppressionEnabled = prefs.noiseSuppressionEnabled,
            dynamicsProcessingEnabled = prefs.dynamicsProcessingEnabled,
            bypassBluetoothChecks = prefs.bypassBluetoothChecks,
            transcriptModeEnabled = prefs.transcriptModeEnabled,
            whisperThreads = prefs.whisperThreads,
            whisperLanguage = prefs.whisperLanguage,
            whisperTranslate = prefs.whisperTranslate,
            isNoiseSuppressionSupported = nsSupported,
            isDynamicsProcessingSupported = dpSupported,
            isDeveloperOptionsEnabled = devEnabled
        )
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState()
    )

    fun setHapticFeedbackEnabled(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setHapticFeedbackEnabled(isEnabled)
        }
    }

    fun setKeepScreenOn(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setKeepScreenOn(isEnabled)
        }
    }

    fun setDisableHearingAidPriority(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setDisableHearingAidPriority(isEnabled)
        }
    }

    fun setMicrophoneGain(gain: Float) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setMicrophoneGain(gain)
        }
    }

    fun setNoiseSuppressionEnabled(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setNoiseSuppressionEnabled(isEnabled)
        }
    }
    
    fun setDynamicsProcessingEnabled(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setDynamicsProcessingEnabled(isEnabled)
        }
    }

    fun setBypassBluetoothChecks(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setBypassBluetoothChecks(isEnabled)
        }
    }

    fun setTranscriptModeEnabled(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setTranscriptModeEnabled(isEnabled)
        }
    }

    fun setWhisperThreads(threads: Int) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setWhisperThreads(threads)
        }
    }

    fun setWhisperLanguage(language: String) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setWhisperLanguage(language)
        }
    }

    fun setWhisperTranslate(isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setWhisperTranslate(isEnabled)
        }
    }

    fun generateAndShareLog() {
        val logFile = HarkLog.getLogFile() ?: return
        
        val contentUri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            logFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Share Hark Log")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(chooser)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            application.contentResolver.unregisterContentObserver(devSettingsObserver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
