package com.thivyanstudios.hark.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thivyanstudios.hark.data.model.UserPreferences
import com.thivyanstudios.hark.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object PreferenceKeys {
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DISABLE_HEARING_AID_PRIORITY = booleanPreferencesKey("disable_hearing_aid_priority")
        val MICROPHONE_GAIN = floatPreferencesKey("microphone_gain")
        val NOISE_SUPPRESSION_ENABLED = booleanPreferencesKey("noise_suppression_enabled")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands") // Storing as comma separated string
        val DYNAMICS_PROCESSING_ENABLED = booleanPreferencesKey("dynamics_processing_enabled")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val bands = parseEqualizerBands(preferences[PreferenceKeys.EQUALIZER_BANDS])
            
            UserPreferences(
                hapticFeedbackEnabled = preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: false,
                keepScreenOn = preferences[PreferenceKeys.KEEP_SCREEN_ON] ?: false,
                disableHearingAidPriority = preferences[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] ?: false,
                microphoneGain = preferences[PreferenceKeys.MICROPHONE_GAIN] ?: Constants.Preferences.DEFAULT_GAIN,
                noiseSuppressionEnabled = preferences[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] ?: false,
                equalizerBands = bands,
                dynamicsProcessingEnabled = preferences[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] ?: false
            )
        }

    suspend fun setHapticFeedbackEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = isEnabled
        }
    }

    suspend fun setKeepScreenOn(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.KEEP_SCREEN_ON] = isEnabled
        }
    }

    suspend fun setDisableHearingAidPriority(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] = isEnabled
        }
    }

    suspend fun setMicrophoneGain(gain: Float) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.MICROPHONE_GAIN] = gain
        }
    }

    suspend fun setNoiseSuppressionEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] = isEnabled
        }
    }
    
    suspend fun setDynamicsProcessingEnabled(isEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] = isEnabled
        }
    }

    suspend fun setEqualizerBand(index: Int, gain: Float) {
        dataStore.edit { preferences ->
            val currentBands = parseEqualizerBands(preferences[PreferenceKeys.EQUALIZER_BANDS]).toMutableList()
            if (index in 0 until Constants.Preferences.EQUALIZER_BAND_COUNT) {
                currentBands[index] = gain
                preferences[PreferenceKeys.EQUALIZER_BANDS] = currentBands.joinToString(",")
            }
        }
    }

    private fun parseEqualizerBands(bandsString: String?): List<Float> {
        val stringToParse = bandsString ?: Constants.Preferences.DEFAULT_EQUALIZER_BANDS
        val parsed = stringToParse.split(",").map { it.toFloatOrNull() ?: Constants.Preferences.DEFAULT_GAIN }
        
        return if (parsed.size == Constants.Preferences.EQUALIZER_BAND_COUNT) {
            parsed
        } else {
            List(Constants.Preferences.EQUALIZER_BAND_COUNT) { Constants.Preferences.DEFAULT_GAIN }
        }
    }
}
