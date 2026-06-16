package com.thivyanstudios.hark.util

object Constants {
    object Navigation {
        const val ROUTE_HOME = "home"
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_EQUALIZER = "equalizer"
        const val ROUTE_HEARING_TEST = "hearing_test"
    }

    object Preferences {
        const val TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_GAIN = 10.0f  // 10dB = ~3x amplification (good starting point for hearing aid)

        const val MIN_MIC_GAIN = -10f
        const val MAX_MIC_GAIN = 30f
        const val MIC_GAIN_STEPS = 39
    }

    object Audio {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SIZE_MULTIPLIER = 2
    }
}
