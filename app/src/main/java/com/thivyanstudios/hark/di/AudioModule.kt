package com.thivyanstudios.hark.di

import com.thivyanstudios.hark.audio.model.AudioEngineEvent
import com.thivyanstudios.hark.audio.processor.AudioProcessor
import com.thivyanstudios.hark.audio.processor.DefaultAudioProcessor
import com.thivyanstudios.hark.audio.stream.AudioStreamManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.Channel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun provideAudioEventChannel(): Channel<AudioEngineEvent> {
        return Channel(Channel.BUFFERED)
    }

    @Provides
    @Singleton
    fun provideAudioProcessor(events: Channel<AudioEngineEvent>): AudioProcessor {
        return DefaultAudioProcessor(events)
    }

    @Provides
    @Singleton
    fun provideAudioStreamManager(audioProcessor: AudioProcessor): AudioStreamManager {
        return AudioStreamManager(audioProcessor)
    }
}
