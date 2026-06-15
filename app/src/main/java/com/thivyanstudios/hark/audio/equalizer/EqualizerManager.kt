package com.thivyanstudios.hark.audio.equalizer

import android.media.audiofx.DynamicsProcessing
import com.thivyanstudios.hark.util.HarkLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor() {

    private var dynamicsProcessing: DynamicsProcessing? = null

    val bandFrequencies = floatArrayOf(125f, 500f, 1000f, 2000f, 4000f)

    data class Preset(val name: String, val gains: FloatArray)

    val presets = listOf(
        Preset("Quiet Room", floatArrayOf(3f, 2f, 1f, 2f, 4f)),
        Preset("Noisy Environment", floatArrayOf(4f, 2f, 0f, -2f, 6f)),
        Preset("TV/Media", floatArrayOf(2f, 1f, 0f, 1f, 3f)),
        Preset("Outdoor", floatArrayOf(3f, 1f, -1f, 1f, 5f))
    )

    private val currentGains = FloatArray(5) { 0f }

    fun initialize(audioSessionId: Int = 0) {
        try {
            release()
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                1,
                true, bandFrequencies.size,
                false, 0,
                false, 0,
                false
            ).build()
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config)
            dynamicsProcessing?.enabled = true
            for (i in currentGains.indices) {
                applyBandGain(i, currentGains[i])
            }
            HarkLog.i(TAG, "DynamicsProcessing initialized on session $audioSessionId")
        } catch (e: Exception) {
            HarkLog.e(TAG, "Failed to initialize DynamicsProcessing", e)
        }
    }

    fun setBandGain(band: Int, gainDb: Float) {
        if (band !in currentGains.indices) return
        currentGains[band] = gainDb.coerceIn(-12f, 12f)
        applyBandGain(band, currentGains[band])
    }

    fun setAllBands(gains: List<Float>) {
        gains.forEachIndexed { i, g ->
            if (i in currentGains.indices) {
                currentGains[i] = g.coerceIn(-12f, 12f)
                applyBandGain(i, currentGains[i])
            }
        }
    }

    fun getAllBands(): List<Float> = currentGains.toList()

    fun applyPreset(index: Int) {
        if (index !in presets.indices) return
        setAllBands(presets[index].gains.toList())
    }

    fun reset() {
        setAllBands(List(5) { 0f })
    }

    fun release() {
        try {
            dynamicsProcessing?.enabled = false
            dynamicsProcessing?.release()
        } catch (_: Exception) {}
        dynamicsProcessing = null
    }

    private fun applyBandGain(band: Int, gain: Float) {
        dynamicsProcessing?.let { dp ->
            try {
                val eqBand = dp.getPreEqBandByChannelIndex(0, band)
                eqBand.isEnabled = true
                eqBand.cutoffFrequency = bandFrequencies[band]
                eqBand.gain = gain
                dp.setPreEqBandAllChannelsTo(band, eqBand)
            } catch (e: Exception) {
                HarkLog.e(TAG, "Failed to set band $band", e)
            }
        }
    }

    companion object {
        private const val TAG = "EqualizerManager"
    }
}
