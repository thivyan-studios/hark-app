package com.thivyanstudios.hark.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        val EQ_BANDS = (1..10).map { floatPreferencesKey("eq_band_${it}_gain") }
        val EQ_LEFT_BANDS = (1..10).map { floatPreferencesKey("eq_left_band_${it}_gain") }
        val EQ_RIGHT_BANDS = (1..10).map { floatPreferencesKey("eq_right_band_${it}_gain") }
        val EQ_LINK_LR = booleanPreferencesKey("eq_link_lr")
        val EQ_BAND_MODE = intPreferencesKey("eq_band_mode")
        val NOISE_REDUCTION_ENABLED = booleanPreferencesKey("noise_reduction_enabled")
        val HEARING_PROTECTION_ENABLED = booleanPreferencesKey("hearing_protection_enabled")
        val LIMITER_THRESHOLD_DB = floatPreferencesKey("limiter_threshold_db")
        val SELECTED_INPUT_DEVICE_ID = intPreferencesKey("selected_input_device_id")
        val SELECTED_OUTPUT_DEVICE_ID = intPreferencesKey("selected_output_device_id")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                HarkLog.e(TAG, "Error reading preferences", exception)
                emit(emptyPreferences())
            } else { throw exception }
        }
        .map { preferences ->
            UserPreferences(
                hapticFeedbackEnabled = preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: false,
                keepScreenOn = preferences[PreferenceKeys.KEEP_SCREEN_ON] ?: false,
                enableBluetoothHeadsetSupport = preferences[PreferenceKeys.ENABLE_BLUETOOTH_HEADSET_SUPPORT] ?: true,
                microphoneGain = preferences[PreferenceKeys.MICROPHONE_GAIN] ?: Constants.Preferences.DEFAULT_GAIN,
                noiseSuppressionEnabled = preferences[PreferenceKeys.NOISE_SUPPRESSION_ENABLED] ?: false,
                dynamicsProcessingEnabled = preferences[PreferenceKeys.DYNAMICS_PROCESSING_ENABLED] ?: false,
                bypassBluetoothChecks = preferences[PreferenceKeys.BYPASS_BLUETOOTH_CHECKS] ?: false,
                preferExternalMic = preferences[PreferenceKeys.PREFER_EXTERNAL_MIC] ?: false,
                eqBandGains = PreferenceKeys.EQ_BANDS.map { preferences[it] ?: 0f },
                eqLeftBandGains = PreferenceKeys.EQ_LEFT_BANDS.map { preferences[it] ?: 0f },
                eqRightBandGains = PreferenceKeys.EQ_RIGHT_BANDS.map { preferences[it] ?: 0f },
                eqLinkLR = preferences[PreferenceKeys.EQ_LINK_LR] ?: true,
                eqBandMode = preferences[PreferenceKeys.EQ_BAND_MODE] ?: 10,
                noiseReductionEnabled = preferences[PreferenceKeys.NOISE_REDUCTION_ENABLED] ?: true,
                hearingProtectionEnabled = preferences[PreferenceKeys.HEARING_PROTECTION_ENABLED] ?: true,
                limiterThresholdDb = preferences[PreferenceKeys.LIMITER_THRESHOLD_DB] ?: -10f,
                selectedInputDeviceId = preferences[PreferenceKeys.SELECTED_INPUT_DEVICE_ID] ?: -1,
                selectedOutputDeviceId = preferences[PreferenceKeys.SELECTED_OUTPUT_DEVICE_ID] ?: -1
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
    suspend fun setEqBandGain(index: Int, gain: Float) { if (index in 0..9) update { it[PreferenceKeys.EQ_BANDS[index]] = gain } }
    suspend fun setEqLeftBandGain(index: Int, gain: Float) { if (index in 0..9) update { it[PreferenceKeys.EQ_LEFT_BANDS[index]] = gain } }
    suspend fun setEqRightBandGain(index: Int, gain: Float) { if (index in 0..9) update { it[PreferenceKeys.EQ_RIGHT_BANDS[index]] = gain } }
    suspend fun setAllEqBandGains(gains: List<Float>) = update { prefs -> gains.forEachIndexed { i, g -> if (i in 0..9) prefs[PreferenceKeys.EQ_BANDS[i]] = g } }
    suspend fun setAllEqLeftBandGains(gains: List<Float>) = update { prefs -> gains.forEachIndexed { i, g -> if (i in 0..9) prefs[PreferenceKeys.EQ_LEFT_BANDS[i]] = g } }
    suspend fun setAllEqRightBandGains(gains: List<Float>) = update { prefs -> gains.forEachIndexed { i, g -> if (i in 0..9) prefs[PreferenceKeys.EQ_RIGHT_BANDS[i]] = g } }
    suspend fun setEqBand1Gain(gain: Float) = setEqBandGain(0, gain)
    suspend fun setEqBand2Gain(gain: Float) = setEqBandGain(1, gain)
    suspend fun setEqBand3Gain(gain: Float) = setEqBandGain(2, gain)
    suspend fun setEqBand4Gain(gain: Float) = setEqBandGain(3, gain)
    suspend fun setEqBand5Gain(gain: Float) = setEqBandGain(4, gain)
    suspend fun setEqLinkLR(linked: Boolean) = update { it[PreferenceKeys.EQ_LINK_LR] = linked }
    suspend fun setEqBandMode(mode: Int) = update { it[PreferenceKeys.EQ_BAND_MODE] = mode }
    suspend fun setNoiseReductionEnabled(isEnabled: Boolean) = update { it[PreferenceKeys.NOISE_REDUCTION_ENABLED] = isEnabled }
    suspend fun setHearingProtectionEnabled(isEnabled: Boolean) = update { it[PreferenceKeys.HEARING_PROTECTION_ENABLED] = isEnabled }
    suspend fun setLimiterThresholdDb(threshold: Float) = update { it[PreferenceKeys.LIMITER_THRESHOLD_DB] = threshold }
    suspend fun setSelectedInputDeviceId(id: Int) = update { it[PreferenceKeys.SELECTED_INPUT_DEVICE_ID] = id }
    suspend fun setSelectedOutputDeviceId(id: Int) = update { it[PreferenceKeys.SELECTED_OUTPUT_DEVICE_ID] = id }

    private suspend fun update(action: (MutablePreferences) -> Unit) {
        try { dataStore.edit { action(it) } } catch (e: Exception) { HarkLog.e(TAG, "Failed to update preferences", e) }
    }
    companion object { private const val TAG = "UserPreferencesRepository" }
}