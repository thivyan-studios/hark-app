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
        
        // Gain and EQ ranges
        const val MIN_MIC_GAIN = -10f
        const val MAX_MIC_GAIN = 30f
        const val MIC_GAIN_STEPS = 39 // (30 - (-10)) * 1 = 40 values, so 39 steps
        
        const val MIN_EQ_GAIN = -10f
        const val MAX_EQ_GAIN = 10f
        const val EQ_GAIN_STEPS = 19
    }
    
    object Audio {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SIZE_MULTIPLIER = 2
    }
}
