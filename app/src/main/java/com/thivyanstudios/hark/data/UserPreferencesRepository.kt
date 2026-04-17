package com.thivyanstudios.hark.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.di.IoDispatcher
import com.thivyanstudios.hark.util.Constants
import com.thivyanstudios.hark.util.HarkLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private object PreferenceKeys {
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DISABLE_HEARING_AID_PRIORITY = booleanPreferencesKey("disable_hearing_aid_priority")
        val MICROPHONE_GAIN = floatPreferencesKey("microphone_gain")
        val NOISE_SUPPRESSION_ENABLED = booleanPreferencesKey("noise_suppression_enabled")
        val DYNAMICS_PROCESSING_ENABLED = booleanPreferencesKey("dynamics_processing_enabled")
        val BYPASS_BLUETOOTH_CHECKS = booleanPreferencesKey("bypass_bluetooth_checks")
        val TRANSCRIPT_MODE_ENABLED = booleanPreferencesKey("transcript_mode_enabled")
        val WHISPER_THREADS = androidx.datastore.preferences.core.intPreferencesKey("whisper_threads")
        val WHISPER_LANGUAGE = androidx.datastore.preferences.core.stringPreferencesKey("whisper_language")
        val WHISPER_TRANSLATE = booleanPreferencesKey("whisper_translate")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                HarkLog.e(TAG, "Error reading preferences", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                hapticFeedbackEnabled = preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: false,
                keepScreenOn = preferences[PreferenceKeys.KEEP_SCREEN_ON] ?: false,
                disableHearingAidPriority = preferences[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] ?: false,
                microphoneGain = preferences[PreferenceKeys.MICROPHONE_GAIN] ?: Constants.Preferences.DEFAULT_GAIN,
                noiseSuppressionEnabled = preferences[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] ?: false,
                dynamicsProcessingEnabled = preferences[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] ?: false,
                bypassBluetoothChecks = preferences[PreferenceKeys.BYPASS_BLUETOOTH_CHECKS] ?: false,
                transcriptModeEnabled = preferences[PreferenceKeys.TRANSCRIPT_MODE_ENABLED] ?: false,
                whisperThreads = preferences[PreferenceKeys.WHISPER_THREADS] ?: 4,
                whisperLanguage = preferences[PreferenceKeys.WHISPER_LANGUAGE] ?: "en",
                whisperTranslate = preferences[PreferenceKeys.WHISPER_TRANSLATE] ?: false
            )
        }
        .flowOn(ioDispatcher)

    suspend fun setHapticFeedbackEnabled(isEnabled: Boolean) = update {
        it[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = isEnabled
    }

    suspend fun setKeepScreenOn(isEnabled: Boolean) = update {
        it[PreferenceKeys.KEEP_SCREEN_ON] = isEnabled
    }

    suspend fun setDisableHearingAidPriority(isEnabled: Boolean) = update {
        it[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] = isEnabled
    }

    suspend fun setMicrophoneGain(gain: Float) = update {
        it[PreferenceKeys.MICROPHONE_GAIN] = gain
    }

    suspend fun setNoiseSuppressionEnabled(isEnabled: Boolean) = update {
        it[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] = isEnabled
    }
    
    suspend fun setDynamicsProcessingEnabled(isEnabled: Boolean) = update {
        it[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] = isEnabled
    }

    suspend fun setBypassBluetoothChecks(isEnabled: Boolean) = update {
        it[PreferenceKeys.BYPASS_BLUETOOTH_CHECKS] = isEnabled
    }

    suspend fun setTranscriptModeEnabled(isEnabled: Boolean) = update {
        it[PreferenceKeys.TRANSCRIPT_MODE_ENABLED] = isEnabled
    }

    suspend fun setWhisperThreads(threads: Int) = update {
        it[PreferenceKeys.WHISPER_THREADS] = threads
    }

    suspend fun setWhisperLanguage(language: String) = update {
        it[PreferenceKeys.WHISPER_LANGUAGE] = language
    }

    suspend fun setWhisperTranslate(isEnabled: Boolean) = update {
        it[PreferenceKeys.WHISPER_TRANSLATE] = isEnabled
    }

    private suspend fun update(action: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit { action(it) }
        } catch (e: Exception) {
            HarkLog.e(TAG, "Failed to update preferences", e)
        }
    }

    companion object {
        private const val TAG = "UserPreferencesRepository"
    }
}
