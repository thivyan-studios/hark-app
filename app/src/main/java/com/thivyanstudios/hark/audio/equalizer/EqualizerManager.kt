package com.thivyanstudios.hark.audio.equalizer

import android.media.audiofx.DynamicsProcessing
import com.thivyanstudios.hark.util.HarkLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerManager @Inject constructor() {

    private var dynamicsProcessing: DynamicsProcessing? = null

    val bandFrequencies = floatArrayOf(125f, 250f, 500f, 1000f, 1500f, 2000f, 3000f, 4000f, 6000f, 8000f)

    val fiveBandIndices = intArrayOf(0, 2, 3, 5, 7)

    data class Preset(val name: String, val gains: FloatArray)

    val presets = listOf(
        Preset("Quiet Room", floatArrayOf(0f, 0f, 1f, 2f, 3f, 4f, 3f, 2f, 0f, 0f)),
        Preset("Noisy Environment", floatArrayOf(-4f, -3f, 0f, 2f, 5f, 6f, 5f, 4f, -2f, -4f)),
        Preset("TV/Media", floatArrayOf(3f, 2f, 1f, 4f, 3f, 4f, 3f, 1f, 0f, 0f)),
        Preset("Outdoor", floatArrayOf(-3f, -2f, 0f, 2f, 4f, 5f, 5f, 4f, 3f, 0f))
    )

    private val currentGainsLeft = FloatArray(10) { 0f }
    private val currentGainsRight = FloatArray(10) { 0f }
    private var linkedLR = true
    private var limiterEnabled = true
    private var limiterThresholdDb = -10f

    fun initialize(audioSessionId: Int = 0) {
        try {
            release()
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, bandFrequencies.size,
                false, 0,
                false, 0,
                true
            ).build()
            dynamicsProcessing = DynamicsProcessing(0, audioSessionId, config)
            dynamicsProcessing?.enabled = true
            for (i in currentGainsLeft.indices) {
                applyBandGain(0, i, currentGainsLeft[i])
                applyBandGain(1, i, currentGainsRight[i])
            }
            applyLimiterSettings()
            HarkLog.i(TAG, "DynamicsProcessing initialized with 10 bands, 2 channels, limiter")
        } catch (e: Exception) {
            HarkLog.e(TAG, "Failed to initialize DynamicsProcessing", e)
        }
    }

    fun setLinkLR(linked: Boolean) {
        linkedLR = linked
        if (linked) {
            currentGainsLeft.copyInto(currentGainsRight)
            for (i in currentGainsRight.indices) {
                applyBandGain(1, i, currentGainsRight[i])
            }
        }
    }

    fun isLinkedLR(): Boolean = linkedLR

    fun setBandGain(band: Int, gainDb: Float, channel: Int = -1) {
        val g = gainDb.coerceIn(-12f, 12f)
        if (band !in 0 until 10) return
        if (channel == -1 || linkedLR) {
            currentGainsLeft[band] = g
            currentGainsRight[band] = g
            applyBandGain(0, band, g)
            applyBandGain(1, band, g)
        } else if (channel == 0) {
            currentGainsLeft[band] = g
            applyBandGain(0, band, g)
        } else {
            currentGainsRight[band] = g
            applyBandGain(1, band, g)
        }
    }

    fun setAllBands(gains: List<Float>, channel: Int = -1) {
        gains.forEachIndexed { i, g ->
            if (i in 0 until 10) setBandGain(i, g, channel)
        }
    }

    fun getAllBands(channel: Int = 0): List<Float> =
        if (channel == 1) currentGainsRight.toList() else currentGainsLeft.toList()

    fun applyPreset(index: Int) {
        if (index !in presets.indices) return
        setAllBands(presets[index].gains.toList())
    }

    fun reset() = setAllBands(List(10) { 0f })

    fun setLimiterEnabled(enabled: Boolean) {
        limiterEnabled = enabled
        applyLimiterSettings()
    }

    fun setLimiterThreshold(thresholdDb: Float) {
        limiterThresholdDb = thresholdDb.coerceIn(-20f, 0f)
        applyLimiterSettings()
    }

    fun getLimiterEnabled(): Boolean = limiterEnabled
    fun getLimiterThreshold(): Float = limiterThresholdDb

    private fun applyLimiterSettings() {
        dynamicsProcessing?.let { dp ->
            try {
                for (ch in 0..1) {
                    val limiter = dp.getLimiterByChannelIndex(ch)
                    limiter.isEnabled = limiterEnabled
                    limiter.attackTime = 1f
                    limiter.releaseTime = 100f
                    limiter.threshold = limiterThresholdDb
                    limiter.ratio = 10f
                    dp.setLimiterByChannelIndex(ch, limiter)
                }
            } catch (e: Exception) {
                HarkLog.e(TAG, "Failed to configure limiter", e)
            }
        }
    }

    fun release() {
        try {
            dynamicsProcessing?.enabled = false
            dynamicsProcessing?.release()
        } catch (_: Exception) {}
        dynamicsProcessing = null
    }

    private fun applyBandGain(channel: Int, band: Int, gain: Float) {
        dynamicsProcessing?.let { dp ->
            try {
                val eqBand = dp.getPreEqBandByChannelIndex(channel, band)
                eqBand.isEnabled = true
                eqBand.cutoffFrequency = bandFrequencies[band]
                eqBand.gain = gain
                dp.setPreEqBandByChannelIndex(channel, band, eqBand)
            } catch (e: Exception) {
                HarkLog.e(TAG, "Failed to set ch=$channel band=$band", e)
            }
        }
    }

    companion object {
        private const val TAG = "EqualizerManager"
    }
}