package com.thivyanstudios.hark.audio.stream

import android.media.AudioRecord

class AndroidAudioSource(private val audioRecord: AudioRecord) : AudioSource {
    override val audioSessionId: Int
        get() = audioRecord.audioSessionId

    override fun start() {
        audioRecord.startRecording()
    }

    override fun stop() {
        audioRecord.stop()
    }

    override fun release() {
        audioRecord.release()
    }

    override fun read(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, readMode: Int): Int {
        return audioRecord.read(audioData, offsetInFloats, sizeInFloats, readMode)
    }
}