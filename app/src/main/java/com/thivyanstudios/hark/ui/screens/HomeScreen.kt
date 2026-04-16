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
) {
    var isButtonEnabled by remember { mutableStateOf(true) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    
    // Pulse animation for the rings
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "PulseScale"
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
        targetValue = if (isStreaming) Color(0xFF4CAF50) else Color(0xFFF44336), // Green when on, Red when off
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
                text = if (isStreaming) "LISTENING" else "READY",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isStreaming) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            HarkText(
                text = if (isStreaming) "Capturing audio..." else "Tap the mic to start",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                scaleX = pulseScale + (index * 0.2f)
                                scaleY = pulseScale + (index * 0.2f)
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
        
        // Connectivity Hint at the bottom
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp + navigationBarsPadding) // Adjusted to be above floating navbar
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isStreaming) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isStreaming) "Whisper AI Active" else "Whisper AI Inactive",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
