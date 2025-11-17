package com.thivyanstudios.hark.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.thivyanstudios.hark.R

@Composable
fun HomeScreen(
    isStreaming: Boolean,
    onStreamButtonClick: () -> Unit,
    hapticFeedbackEnabled: Boolean,
    innerPadding: PaddingValues
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = ""
    )
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        // This is your stream button (ImageView)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp) // Sets a fixed size for the image
                .scale(scale)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isStreaming) Color(0xFF4B5320) else Color.Red)
                .align(Alignment.Center) // Centers the image in the Box
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = {
                            if (hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onStreamButtonClick()
                        }
                    )
                }
        ) {
            Image(
                painter = if (isStreaming) {
                    painterResource(id = R.drawable.ic_mic_on)
                } else {
                    painterResource(id = R.drawable.ic_mic_off)
                },
                contentDescription = "Button to stream",
                modifier = Modifier.size(100.dp)
            )
        }
    }
}