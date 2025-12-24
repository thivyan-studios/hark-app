package com.thivyanstudios.hark.audio.stream

interface AudioSource {
    val audioSessionId: Int
    fun start()
    fun stop()
    fun release()
    fun read(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, readMode: Int): Int
}