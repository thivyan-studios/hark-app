package com.thivyanstudios.hark.util

import android.app.Application
import android.provider.Settings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemSettingsProvider @Inject constructor(private val application: Application) {
    fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(
            application.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0
    }
    
    val developmentSettingsUri = Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)
}
