package com.thivyanstudios.hark.audio.stream

interface AudioSink {
    val audioSessionId: Int
    val bufferSizeInFrames: Int
    fun play()
    fun stop()
    fun release()
    fun write(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, writeMode: Int): Int
}