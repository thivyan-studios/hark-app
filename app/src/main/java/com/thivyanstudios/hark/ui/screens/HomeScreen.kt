package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.BetaBadge
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.theme.GlassOrbButton
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    isStreaming: Boolean,
    onStreamButtonClick: () -> Unit,
    hapticFeedbackEnabled: Boolean,
    isModelAvailable: Boolean
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

    // User requested to preserve red and green button colors
    val greenColor = Color(0xFF4CAF50)
    val redColor = Color(0xFFF44336)

    val buttonColor by animateColorAsState(
        targetValue = if (isStreaming) greenColor else redColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ButtonColor"
    )

    LaunchedEffect(isStreaming) {
        isButtonEnabled = true
    }

    LaunchedEffect(isButtonEnabled) {
        if (!isButtonEnabled) {
            delay(1000L)
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
                text = if (isStreaming) "Streaming" else "Ready",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isStreaming) greenColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            HarkText(
                text = if (isStreaming) "Tap to stop" else "Tap the mic to start",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            )
        }

        // Center Mic Button Area
        Box(
            modifier = Modifier.align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Liquid-glass mic button. The pulse rings sit behind the glass so they
            // ripple through it while streaming.
            GlassOrbButton(
                color = buttonColor,
                onClick = {
                    if (isButtonEnabled) {
                        isButtonEnabled = false
                        onStreamButtonClick()
                    }
                },
                enabled = isButtonEnabled,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                backdrop = {
                    if (isStreaming) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .graphicsLayer {
                                        val finalScale = basePulseScale + (index * 0.15f)
                                        scaleX = finalScale
                                        scaleY = finalScale
                                        alpha = pulseAlpha / (index + 1)
                                    }
                                    .background(buttonColor.copy(alpha = 0.35f), CircleShape)
                                    .border(1.dp, buttonColor.copy(alpha = 0.6f), CircleShape)
                            )
                        }
                    }
                },
            ) {
                Image(
                    painter = if (isStreaming) {
                        painterResource(id = R.drawable.ic_mic_on)
                    } else {
                        painterResource(id = R.drawable.ic_mic_off)
                    },
                    contentDescription = stringResource(R.string.cd_stream_button),
                    modifier = Modifier.size(76.dp)
                )
            }
        }
        
        // M3 Styled Activity Chip at the bottom
        val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        
        if (isStreaming && isModelAvailable) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp + navigationBarsPadding),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(greenColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sherpa-ONNX is running",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BetaBadge()
                }
            }
        }
    }
}
