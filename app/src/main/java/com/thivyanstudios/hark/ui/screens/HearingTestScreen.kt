package com.thivyanstudios.hark.ui.screens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thivyanstudios.hark.ui.theme.HarkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HearingTestScreen(
    onApplyToEq: (List<Float>) -> Unit = {}
) {
    val testFrequencies = listOf(250, 500, 1000, 2000, 4000, 8000)
    val freqLabels = listOf("250", "500", "1k", "2k", "4k", "8k")
    var currentFreqIndex by remember { mutableIntStateOf(0) }
    var currentVolume by remember { mutableFloatStateOf(0.5f) }
    var isPlaying by remember { mutableStateOf(false) }
    var testComplete by remember { mutableStateOf(false) }
    val thresholds = remember { mutableStateMapOf<Int, Float>() }
    val scope = rememberCoroutineScope()

    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            audioTrack?.stop()
            audioTrack?.release()
        }
    }

    fun playTone(frequency: Int, volume: Float) {
        scope.launch {
            withContext(Dispatchers.IO) {
                audioTrack?.stop()
                audioTrack?.release()

                val sampleRate = 44100
                val durationSec = 1.5
                val numSamples = (durationSec * sampleRate).toInt()
                val fadeLen = (0.05 * sampleRate).toInt()
                val samples = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    var amp = sin(2.0 * PI * frequency * i / sampleRate) * volume
                    // Fade in/out to avoid clicks
                    if (i < fadeLen) amp *= i.toDouble() / fadeLen
                    if (i > numSamples - fadeLen) amp *= (numSamples - i).toDouble() / fadeLen
                    samples[i] = (amp * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val at = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                at.write(samples, 0, numSamples)
                audioTrack = at
                isPlaying = true
                at.play()
            }
            delay(1500)
            isPlaying = false
        }
    }

    fun calculateEqCompensation(): List<Float> {
        // Map hearing thresholds to EQ gains
        // Lower threshold = worse hearing = more boost needed
        // Frequencies: test has 250,500,1k,2k,4k,8k -> EQ has 125,500,1k,2k,4k
        val eqFreqs = listOf(250, 500, 1000, 2000, 4000)
        val referenceThreshold = 0.1f // normal hearing ~10%
        return eqFreqs.map { freq ->
            val threshold = thresholds[freq] ?: thresholds.entries.minByOrNull {
                kotlin.math.abs(it.key - freq)
            }?.value ?: referenceThreshold
            // Higher threshold means worse hearing -> more boost
            val boost = ((threshold - referenceThreshold) * 60f).coerceIn(-12f, 12f)
            boost
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            HarkText(
                text = "HEARING TEST",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            HarkText(
                text = "Find the quietest level you can hear at each frequency",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!testComplete) {
                // Test card
                SettingsGroup(title = "Test Tone") {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HarkText(
                                text = "Frequency: ${testFrequencies[currentFreqIndex]} Hz",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            HarkText(
                                text = "Step ${currentFreqIndex + 1}/${testFrequencies.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            HarkText(text = "Volume")
                            HarkText(
                                text = String.format(Locale.US, "%d%%", (currentVolume * 100).toInt()),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Slider(
                            value = currentVolume,
                            onValueChange = { currentVolume = it },
                            valueRange = 0.01f..1f
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { playTone(testFrequencies[currentFreqIndex], currentVolume) },
                                enabled = !isPlaying,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isPlaying) "Playing..." else "Play")
                            }

                            Button(
                                onClick = {
                                    thresholds[testFrequencies[currentFreqIndex]] = currentVolume
                                    if (currentFreqIndex < testFrequencies.size - 1) {
                                        currentFreqIndex++
                                        currentVolume = 0.5f
                                    } else {
                                        testComplete = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("I Hear It")
                            }
                        }
                    }
                }

                // Progress chips
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    testFrequencies.forEachIndexed { i, freq ->
                        val done = thresholds.containsKey(freq)
                        val current = i == currentFreqIndex
                        FilterChip(
                            selected = done || current,
                            onClick = {},
                            label = { Text(freqLabels[i], style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (done) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }

            // Audiogram
            if (thresholds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                SettingsGroup(title = "Audiogram") {
                    val primary = MaterialTheme.colorScheme.primary
                    val onSurface = MaterialTheme.colorScheme.onSurface

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(8.dp)
                    ) {
                        val barW = size.width / testFrequencies.size
                        val maxH = size.height - 30f

                        testFrequencies.forEachIndexed { i, freq ->
                            val threshold = thresholds[freq] ?: 0f
                            val barH = maxH * threshold

                            drawRect(
                                color = primary,
                                topLeft = Offset(i * barW + barW * 0.15f, maxH - barH),
                                size = Size(barW * 0.7f, barH)
                            )

                            drawContext.canvas.nativeCanvas.drawText(
                                freqLabels[i],
                                i * barW + barW * 0.5f,
                                size.height,
                                android.graphics.Paint().apply {
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    textSize = 28f
                                    color = onSurface.hashCode()
                                }
                            )
                        }
                    }
                }
            }

            if (testComplete) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val compensation = calculateEqCompensation()
                        onApplyToEq(compensation)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply to Equalizer")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    thresholds.clear()
                    currentFreqIndex = 0
                    currentVolume = 0.5f
                    testComplete = false
                    audioTrack?.stop()
                    audioTrack?.release()
                    audioTrack = null
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restart Test")
            }

            Spacer(modifier = Modifier.height(160.dp))
        }

        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )
    }
}
