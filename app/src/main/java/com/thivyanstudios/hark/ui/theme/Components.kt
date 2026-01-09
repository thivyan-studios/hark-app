package com.thivyanstudios.hark.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SquishyBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    disabledBackgroundColor: Color? = null,
    cornerRadius: Dp = 12.dp,
    contentAlignment: Alignment = Alignment.Center,
    enabled: Boolean = true,
    hapticFeedbackEnabled: Boolean = true, // Added preference check
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current

    val targetColor = if (enabled) backgroundColor else (disabledBackgroundColor ?: backgroundColor)

    val animatedBackgroundColor by animateColorAsState(
        targetValue = targetColor,
        label = "BackgroundColorAnimation"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    launch {
                        scale.animateTo(
                            targetValue = 0.9f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = 2000f
                            )
                        )
                    }
                }
                is PressInteraction.Release -> {
                    launch {
                        if (scale.value > 0.95f) {
                            scale.animateTo(
                                targetValue = 0.9f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessHigh
                                )
                            )
                        }
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                }
                is PressInteraction.Cancel -> {
                    launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                }
            }
        }
    }

    Box(
        contentAlignment = contentAlignment,
        modifier = modifier
            .scale(scale.value)
            .clip(RoundedCornerShape(cornerRadius))
            .background(animatedBackgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                if (hapticFeedbackEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            },
        content = content
    )
}
