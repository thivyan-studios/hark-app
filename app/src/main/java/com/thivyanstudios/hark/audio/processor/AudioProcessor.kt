package com.thivyanstudios.hark.audio.processor

import android.media.AudioRecord
import android.media.AudioTrack
import com.thivyanstudios.hark.audio.model.AudioProcessingConfig

interface AudioProcessor {
    fun process(
        audioRecord: AudioRecord,
        audioTrack: AudioTrack,
        config: AudioProcessingConfig,
        isRunning: () -> Boolean
    )
    fun updateConfig(config: AudioProcessingConfig)
}
