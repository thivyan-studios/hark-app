package com.thivyanstudios.hark.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thivyanstudios.hark.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_PREFERENCES_NAME = "settings"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_PREFERENCES_NAME)

class UserPreferencesRepository(private val context: Context) {

    private object PreferenceKeys {
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DISABLE_HEARING_AID_PRIORITY = booleanPreferencesKey("disable_hearing_aid_priority")
        val MICROPHONE_GAIN = floatPreferencesKey("microphone_gain")
        val NOISE_SUPPRESSION_ENABLED = booleanPreferencesKey("noise_suppression_enabled")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands") // Storing as comma separated string
        val DYNAMICS_PROCESSING_ENABLED = booleanPreferencesKey("dynamics_processing_enabled")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            val bandsString = preferences[PreferenceKeys.EQUALIZER_BANDS] ?: "0.0,0.0,0.0,0.0,0.0"
            val bands = bandsString.split(",").map { it.toFloatOrNull() ?: 0.0f }
            
            UserPreferences(
                hapticFeedbackEnabled = preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: false,
                keepScreenOn = preferences[PreferenceKeys.KEEP_SCREEN_ON] ?: false,
                disableHearingAidPriority = preferences[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] ?: false,
                microphoneGain = preferences[PreferenceKeys.MICROPHONE_GAIN] ?: 0.0f,
                noiseSuppressionEnabled = preferences[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] ?: false,
                equalizerBands = if (bands.size == 5) bands else listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                dynamicsProcessingEnabled = preferences[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] ?: false
            )
        }

    suspend fun setHapticFeedbackEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = isEnabled
        }
    }

    suspend fun setKeepScreenOn(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.KEEP_SCREEN_ON] = isEnabled
        }
    }

    suspend fun setDisableHearingAidPriority(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] = isEnabled
        }
    }

    suspend fun setMicrophoneGain(gain: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MICROPHONE_GAIN] = gain
        }
    }

    suspend fun setNoiseSuppressionEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] = isEnabled
        }
    }
    
    suspend fun setDynamicsProcessingEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] = isEnabled
        }
    }

    suspend fun setEqualizerBand(index: Int, gain: Float) {
        context.dataStore.edit { preferences ->
            val currentBandsString = preferences[PreferenceKeys.EQUALIZER_BANDS] ?: "0.0,0.0,0.0,0.0,0.0"
            val currentBands = currentBandsString.split(",").map { it.toFloatOrNull() ?: 0.0f }.toMutableList()
            if (currentBands.size != 5) {
                currentBands.clear()
                currentBands.addAll(listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f))
            }
            if (index in 0 until 5) {
                currentBands[index] = gain
                preferences[PreferenceKeys.EQUALIZER_BANDS] = currentBands.joinToString(",")
            }
        }
    }
}
