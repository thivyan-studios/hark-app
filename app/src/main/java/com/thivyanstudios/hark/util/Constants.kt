package com.thivyanstudios.hark.util

object Constants {
    object Navigation {
        const val ROUTE_HOME = "home"
        const val ROUTE_SETTINGS = "settings"
    }

    object Preferences {
        const val TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_EQUALIZER_BANDS = "0.0,0.0,0.0,0.0,0.0"
        const val DEFAULT_GAIN = 0.0f
        
        // Centralized frequency configuration
        val EQUALIZER_FREQUENCIES = listOf("60Hz", "230Hz", "910Hz", "3kHz", "14kHz")
        val EQUALIZER_BAND_COUNT = EQUALIZER_FREQUENCIES.size
    }
}
