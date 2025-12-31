package com.thivyanstudios.hark.audio.stream

import java.util.Random

class PinkNoiseAudioSource : AudioSource {
    override val audioSessionId: Int = 0 // Session 0 for generated content

    private val random = Random()
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    override fun start() {
        // Reset state
        b0 = 0f; b1 = 0f; b2 = 0f; b3 = 0f; b4 = 0f; b5 = 0f; b6 = 0f
    }

    override fun stop() {
        // No-op for generator
    }

    override fun release() {
        // No-op for generator
    }

    override fun read(audioData: FloatArray, offsetInFloats: Int, sizeInFloats: Int, readMode: Int): Int {
        for (i in 0 until sizeInFloats) {
            val white = random.nextFloat() * 2 - 1
            
            // Paul Kellett's refined method for Pink Noise generation
            b0 = 0.99886f * b0 + white * 0.0555179f
            b1 = 0.99332f * b1 + white * 0.0750759f
            b2 = 0.96900f * b2 + white * 0.1538520f
            b3 = 0.86650f * b3 + white * 0.3104856f
            b4 = 0.55000f * b4 + white * 0.5329522f
            b5 = -0.7616f * b5 - white * 0.0168980f
            val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
            b6 = white * 0.115926f
            
            // Normalize roughly to -1.0 to 1.0 (approximated gain adjustment)
            audioData[offsetInFloats + i] = pink * PINK_NOISE_GAIN
        }
        return sizeInFloats
    }

    companion object {
        private const val PINK_NOISE_GAIN = 0.11f
    }
}
