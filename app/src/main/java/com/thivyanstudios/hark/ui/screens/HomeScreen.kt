package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.theme.SquishyBox
import com.thivyanstudios.hark.util.Constants
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    isStreaming: Boolean,
    onStreamButtonClick: () -> Unit,
    hapticFeedbackEnabled: Boolean,
    inputLevel: Float = 0f,
    outputLevel: Float = 0f,
    isConnected: Boolean = false,
    latencyMs: Float = 0f,
    inputDeviceName: String = "",
    outputDeviceName: String = ""
) {
    var isButtonEnabled by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")

    val basePulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "BasePulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "PulseAlpha"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isStreaming) Color(0xFF4CAF50) else Color(0xFFF44336),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ButtonColor"
    )

    LaunchedEffect(isStreaming) {
        isButtonEnabled = true
    }

    LaunchedEffect(isButtonEnabled) {
        if (!isButtonEnabled) {
            delay(5000L)
            isButtonEnabled = true
        }
    }

    val deviceColor = if (isStreaming)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val inputName = when {
        !isConnected -> "Connecting..."
        inputDeviceName.isEmpty() -> "Built-in Mic"
        else -> inputDeviceName.take(24)
    }
    val outputName = when {
        !isConnected -> "Connecting..."
        outputDeviceName.isEmpty() -> "Speaker"
        else -> outputDeviceName.take(24)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Level meters at top
        if (isConnected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AudioLevelBar(
                    label = "MIC IN",
                    level = inputLevel,
                    color = Color(0xFF4CAF50),
                    isStreaming = isStreaming
                )
                Spacer(modifier = Modifier.height(6.dp))
                AudioLevelBar(
                    label = "EAR OUT",
                    level = outputLevel,
                    color = Color(0xFF2196F3),
                    isStreaming = isStreaming
                )
                if (isStreaming && inputLevel < 0.0001f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HarkText(
                        text = "\u26a0\ufe0f No microphone input detected",
                        color = Color(0xFFFF5722),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Center content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Input device name
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\uD83C\uDF99 ", style = MaterialTheme.typography.bodySmall)
                    Text(inputName, style = MaterialTheme.typography.bodySmall, color = deviceColor)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status text
                HarkText(
                    text = if (isStreaming) "STREAMING" else "READY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStreaming) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                HarkText(
                    text = if (isStreaming) "Tap to stop" else "Tap the mic to start",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Big button with pulse rings
                Box(contentAlignment = Alignment.Center) {
                    if (isStreaming) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .graphicsLayer {
                                        val finalScale = basePulseScale + (index * 0.15f)
                                        scaleX = finalScale
                                        scaleY = finalScale
                                        alpha = pulseAlpha / (index + 1)
                                    }
                                    .background(buttonColor.copy(alpha = 0.4f), CircleShape)
                                    .border(1.dp, buttonColor, CircleShape)
                            )
                        }
                    }

                    SquishyBox(
                        onClick = {
                            if (isButtonEnabled) {
                                isButtonEnabled = false
                                onStreamButtonClick()
                            }
                        },
                        modifier = Modifier.size(140.dp),
                        backgroundColor = buttonColor,
                        enabled = isButtonEnabled,
                        hapticFeedbackEnabled = hapticFeedbackEnabled
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = if (isStreaming) {
                                    painterResource(id = R.drawable.ic_mic_on)
                                } else {
                                    painterResource(id = R.drawable.ic_mic_off)
                                },
                                contentDescription = stringResource(R.string.cd_stream_button),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Output device name
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\uD83C\uDFA7 ", style = MaterialTheme.typography.bodySmall)
                    Text(outputName, style = MaterialTheme.typography.bodySmall, color = deviceColor)
                }
            }
        }

        // Info chips row at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val latencyColor = when {
                !isStreaming -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                latencyMs < 30f -> Color(0xFF4CAF50)
                latencyMs < 80f -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            }
            val latencyLabel = if (isStreaming && latencyMs > 0f)
                "\u23F1 ~${latencyMs.toInt()}ms" else "\u23F1 --ms"

            SuggestionChip(
                onClick = {},
                label = { Text(latencyLabel, color = latencyColor, style = MaterialTheme.typography.labelSmall) }
            )
            SuggestionChip(
                onClick = {},
                label = { Text("\uD83D\uDCCA ${Constants.Audio.SAMPLE_RATE / 1000}kHz", style = MaterialTheme.typography.labelSmall) }
            )
            SuggestionChip(
                onClick = {},
                label = { Text("\uD83D\uDD0B Java", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun AudioLevelBar(label: String, level: Float, color: androidx.compose.ui.graphics.Color, isStreaming: Boolean) {
    val displayLevel = if (isStreaming) level.coerceIn(0f, 1f) else 0f
    val dbValue = if (displayLevel > 0.001f) (20 * kotlin.math.log10(displayLevel.toDouble())).toInt() else -60

    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HarkText(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = androidx.compose.ui.Modifier.width(56.dp)
        )
        Box(
            modifier = androidx.compose.ui.Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(displayLevel)
                    .background(color)
            )
        }
        HarkText(
            text = "${dbValue}dB",
            style = MaterialTheme.typography.labelSmall,
            modifier = androidx.compose.ui.Modifier.width(40.dp).padding(start = 4.dp)
        )
    }
}
