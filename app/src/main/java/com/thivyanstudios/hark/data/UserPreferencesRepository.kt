package com.thivyanstudios.hark.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UserPreferencesRepository(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private object PreferenceKeys {
        const val HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
        const val IS_DARK_MODE = "is_dark_mode"
        const val KEEP_SCREEN_ON = "keep_screen_on"
        const val DISABLE_HEARING_AID_PRIORITY = "disable_hearing_aid_priority"
    }

    val hapticFeedbackEnabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceKeys.HAPTIC_FEEDBACK_ENABLED) {
                trySend(sharedPreferences.getBoolean(key, false))
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(sharedPreferences.getBoolean(PreferenceKeys.HAPTIC_FEEDBACK_ENABLED, false))
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setHapticFeedbackEnabled(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceKeys.HAPTIC_FEEDBACK_ENABLED, isEnabled) }
    }

    val isDarkMode: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceKeys.IS_DARK_MODE) {
                trySend(sharedPreferences.getBoolean(key, true))
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(sharedPreferences.getBoolean(PreferenceKeys.IS_DARK_MODE, true))
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setIsDarkMode(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceKeys.IS_DARK_MODE, isEnabled) }
    }

    val keepScreenOn: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceKeys.KEEP_SCREEN_ON) {
                trySend(sharedPreferences.getBoolean(key, false))
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(sharedPreferences.getBoolean(PreferenceKeys.KEEP_SCREEN_ON, false))
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setKeepScreenOn(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceKeys.KEEP_SCREEN_ON, isEnabled) }
    }

    val disableHearingAidPriority: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceKeys.DISABLE_HEARING_AID_PRIORITY) {
                trySend(sharedPreferences.getBoolean(key, false))
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(sharedPreferences.getBoolean(PreferenceKeys.DISABLE_HEARING_AID_PRIORITY, false))
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setDisableHearingAidPriority(isEnabled: Boolean) {
        sharedPreferences.edit { putBoolean(PreferenceKeys.DISABLE_HEARING_AID_PRIORITY, isEnabled) }
    }
}
