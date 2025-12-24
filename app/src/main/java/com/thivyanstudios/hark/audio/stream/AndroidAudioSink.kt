package com.thivyanstudios.hark.audio.stream

import android.media.AudioTrack

class AndroidAudioSink(private val audioTrack: AudioTrack) : AudioSink {
    override val audioSessionId: Int
        get() = audioTrack.audioSessionId

    override val bufferSizeInFrames: Int
        get() = audioTrack.bufferSizeInFrames

    override fun play() {
        audioTrack.play()
    }

    override fun stop() {
        audioTrack.stop()
    }

    override fun release() {
        audioTrack.release()
    }

    override fun write(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, writeMode: Int): Int {
        return audioTrack.write(audioData, offsetInFloats, sizeInFloats, writeMode)
    }
}