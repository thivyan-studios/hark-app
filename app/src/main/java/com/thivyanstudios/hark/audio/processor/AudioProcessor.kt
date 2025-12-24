package com.thivyanstudios.hark.audio.processor

import com.thivyanstudios.hark.audio.model.AudioProcessingConfig
import com.thivyanstudios.hark.audio.stream.AudioSink
import com.thivyanstudios.hark.audio.stream.AudioSource

interface AudioProcessor {
    fun process(
        audioSource: AudioSource,
        audioSink: AudioSink,
        config: AudioProcessingConfig,
        isRunning: () -> Boolean
    )
    fun updateConfig(config: AudioProcessingConfig)
}