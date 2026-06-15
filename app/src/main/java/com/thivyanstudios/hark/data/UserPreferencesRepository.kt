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
        val ENABLE_BLUETOOTH_HEADSET_SUPPORT = booleanPreferencesKey("enable_bluetooth_headset_support")
        val MICROPHONE_GAIN = floatPreferencesKey("microphone_gain")
        val NOISE_SUPPRESSION_ENABLED = booleanPreferencesKey("noise_suppression_enabled")
        val DYNAMICS_PROCESSING_ENABLED = booleanPreferencesKey("dynamics_processing_enabled")
        val BYPASS_BLUETOOTH_CHECKS = booleanPreferencesKey("bypass_bluetooth_checks")
        val PREFER_EXTERNAL_MIC = booleanPreferencesKey("prefer_external_mic")
        val EQ_BAND_1 = floatPreferencesKey("eq_band_1_gain")
        val EQ_BAND_2 = floatPreferencesKey("eq_band_2_gain")
        val EQ_BAND_3 = floatPreferencesKey("eq_band_3_gain")
        val EQ_BAND_4 = floatPreferencesKey("eq_band_4_gain")
        val EQ_BAND_5 = floatPreferencesKey("eq_band_5_gain")
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
                enableBluetoothHeadsetSupport = preferences[PreferenceKeys.ENABLE_BLUETOOTH_HEADSET_SUPPORT] ?: false,
                microphoneGain = preferences[PreferenceKeys.MICROPHONE_GAIN] ?: Constants.Preferences.DEFAULT_GAIN,
                noiseSuppressionEnabled = preferences[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] ?: false,
                dynamicsProcessingEnabled = preferences[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] ?: false,
                bypassBluetoothChecks = preferences[PreferenceKeys.BYPASS_BLUETOOTH_CHECKS] ?: false,
                preferExternalMic = preferences[PreferenceKeys.PREFER_EXTERNAL_MIC] ?: false,
                eqBand1Gain = preferences[PreferenceKeys.EQ_BAND_1] ?: 0f,
                eqBand2Gain = preferences[PreferenceKeys.EQ_BAND_2] ?: 0f,
                eqBand3Gain = preferences[PreferenceKeys.EQ_BAND_3] ?: 0f,
                eqBand4Gain = preferences[PreferenceKeys.EQ_BAND_4] ?: 0f,
                eqBand5Gain = preferences[PreferenceKeys.EQ_BAND_5] ?: 0f
            )
        }
        .flowOn(ioDispatcher)

    suspend fun setHapticFeedbackEnabled(isEnabled: Boolean) = update { it[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = isEnabled }
    suspend fun setKeepScreenOn(isEnabled: Boolean) = update { it[PreferenceKeys.KEEP_SCREEN_ON] = isEnabled }
    suspend fun setEnableBluetoothHeadsetSupport(isEnabled: Boolean) = update { it[PreferenceKeys.ENABLE_BLUETOOTH_HEADSET_SUPPORT] = isEnabled }
    suspend fun setMicrophoneGain(gain: Float) = update { it[PreferenceKeys.MICROPHONE_GAIN] = gain }
    suspend fun setNoiseSuppressionEnabled(isEnabled: Boolean) = update { it[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] = isEnabled }
    suspend fun setDynamicsProcessingEnabled(isEnabled: Boolean) = update { it[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] = isEnabled }
    suspend fun setBypassBluetoothChecks(isEnabled: Boolean) = update { it[PreferenceKeys.BYPASS_BLUETOOTH_CHECKS] = isEnabled }
    suspend fun setPreferExternalMic(enabled: Boolean) = update { it[PreferenceKeys.PREFER_EXTERNAL_MIC] = enabled }
    suspend fun setEqBand1Gain(gain: Float) = update { it[PreferenceKeys.EQ_BAND_1] = gain }
    suspend fun setEqBand2Gain(gain: Float) = update { it[PreferenceKeys.EQ_BAND_2] = gain }
    suspend fun setEqBand3Gain(gain: Float) = update { it[PreferenceKeys.EQ_BAND_3] = gain }
    suspend fun setEqBand4Gain(gain: Float) = update { it[PreferenceKeys.EQ_BAND_4] = gain }
    suspend fun setEqBand5Gain(gain: Float) = update { it[PreferenceKeys.EQ_BAND_5] = gain }

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
