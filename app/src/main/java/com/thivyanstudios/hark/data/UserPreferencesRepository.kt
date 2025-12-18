package com.thivyanstudios.hark.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_PREFERENCES_NAME = "settings"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_PREFERENCES_NAME)

class UserPreferencesRepository(private val context: Context) {

    private object PreferenceKeys {
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DISABLE_HEARING_AID_PRIORITY = booleanPreferencesKey("disable_hearing_aid_priority")
        val MICROPHONE_GAIN = floatPreferencesKey("microphone_gain")
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] ?: false
        }

    suspend fun setHapticFeedbackEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.HAPTIC_FEEDBACK_ENABLED] = isEnabled
        }
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.IS_DARK_MODE] ?: true
        }

    suspend fun setIsDarkMode(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.IS_DARK_MODE] = isEnabled
        }
    }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.KEEP_SCREEN_ON] ?: false
        }

    suspend fun setKeepScreenOn(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.KEEP_SCREEN_ON] = isEnabled
        }
    }

    val disableHearingAidPriority: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] ?: false
        }

    suspend fun setDisableHearingAidPriority(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.DISABLE_HEARING_AID_PRIORITY] = isEnabled
        }
    }

    val microphoneGain: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.MICROPHONE_GAIN] ?: 0.0f
        }

    suspend fun setMicrophoneGain(gain: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.MICROPHONE_GAIN] = gain
        }
    }
}
