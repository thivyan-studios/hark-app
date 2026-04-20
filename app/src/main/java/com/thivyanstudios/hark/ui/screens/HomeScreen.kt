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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    isStreaming: Boolean,
    onStreamButtonClick: () -> Unit,
    hapticFeedbackEnabled: Boolean,
    audioLevel: Float = 0f
) {
    var isButtonEnabled by remember { mutableStateOf(true) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    
    // Base pulse animation
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

    // Reactive level scale - adds to the base pulse when there's sound
    val levelScale by animateFloatAsState(
        targetValue = audioLevel * 2.5f, // Amplify for visual effect
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "LevelScale"
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Status Text at the Top
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HarkText(
                text = if (isStreaming) "STREAMING" else "READY",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isStreaming) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            HarkText(
                text = if (isStreaming) "Tap to stop" else "Tap the mic to start",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }

        // Center Mic Button Area
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing Rings (Only visible when streaming)
            if (isStreaming) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                // Mix the base pulse with the live audio level
                                val finalScale = basePulseScale + (index * 0.15f) + levelScale
                                scaleX = finalScale
                                scaleY = finalScale
                                alpha = pulseAlpha / (index + 1)
                            }
                            .background(buttonColor.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, buttonColor, CircleShape)
                    )
                }
            }

            // The Main Button
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
        
        // Activity Chip at the bottom
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val isCapturing = isStreaming && audioLevel > 0.005f
        
        if (isStreaming) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp + navigationBarsPadding)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isCapturing) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCapturing) "Whisper is Active" else "Whisper is Idle",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
