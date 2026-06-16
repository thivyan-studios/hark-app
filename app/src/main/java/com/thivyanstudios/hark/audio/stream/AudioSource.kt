package com.thivyanstudios.hark.audio.stream

import android.media.AudioDeviceInfo

interface AudioSource {
    val audioSessionId: Int
    fun start()
    fun stop()
    fun release()
    fun read(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, readMode: Int): Int
    fun setPreferredDevice(device: AudioDeviceInfo?): Boolean = false
}
