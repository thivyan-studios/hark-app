package com.thivyanstudios.hark.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Asks Android for "audio focus" while HARK streams, and reports when another app takes it:
 * a phone call or alarm (temporary), or another app's playback taking over for good.
 */
class AudioFocusController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    enum class Change {
        /** Another app took over for good: stop streaming. */
        Lost,
        /** A call, alarm or assistant needs audio for a while: pause. */
        LostTemporarily,
        /** Focus came back after a temporary loss: resume. */
        Regained,
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null

    /** Asks for focus. Returns false if Android refused (e.g. during a call). */
    fun request(onChange: (Change) -> Unit): Boolean {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        HarkLog.i(TAG, "Audio focus lost permanently")
                        onChange(Change.Lost)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        HarkLog.i(TAG, "Audio focus lost temporarily")
                        onChange(Change.LostTemporarily)
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        HarkLog.i(TAG, "Audio focus regained")
                        onChange(Change.Regained)
                    }
                    // "Duck" requests (e.g. a navigation prompt) ask apps to play quieter. HARK
                    // keeps relaying, since cutting someone's hearing aid audio is worse.
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        HarkLog.i(TAG, "Audio focus lost (duckable), continuing")
                }
            }
            .build()
        request = focusRequest
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /** Gives focus back so other apps' audio returns to normal. */
    fun abandon() {
        request?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private companion object {
        const val TAG = "AudioFocusController"
    }
}
