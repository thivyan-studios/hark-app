package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.util.Constants.Navigation

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    hapticFeedbackEnabled: Boolean,
    isModelAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp + navigationBarsPadding, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            tonalElevation = 10.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedNavigationBarItem(
                    icon = Icons.Rounded.Mic,
                    labelResId = R.string.nav_stream,
                    selected = currentRoute == Navigation.ROUTE_HOME,
                    onClick = { onNavigate(Navigation.ROUTE_HOME) },
                    hapticFeedbackEnabled = hapticFeedbackEnabled
                )
                if (isModelAvailable) {
                    AnimatedNavigationBarItem(
                        icon = Icons.Rounded.RecordVoiceOver,
                        labelResId = R.string.nav_transcribe,
                        selected = currentRoute == Navigation.ROUTE_TRANSCRIBE,
                        onClick = { onNavigate(Navigation.ROUTE_TRANSCRIBE) },
                        hapticFeedbackEnabled = hapticFeedbackEnabled
                    )
                    AnimatedNavigationBarItem(
                        icon = Icons.Rounded.History,
                        labelResId = R.string.nav_history,
                        selected = currentRoute == Navigation.ROUTE_HISTORY,
                        onClick = { onNavigate(Navigation.ROUTE_HISTORY) },
                        hapticFeedbackEnabled = hapticFeedbackEnabled
                    )
                }
                AnimatedNavigationBarItem(
                    icon = Icons.Rounded.Settings,
                    labelResId = R.string.nav_settings,
                    selected = currentRoute == Navigation.ROUTE_SETTINGS,
                    onClick = { onNavigate(Navigation.ROUTE_SETTINGS) },
                    hapticFeedbackEnabled = hapticFeedbackEnabled
                )
            }
        }
    }
}

@Composable
fun AnimatedNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    labelResId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hapticFeedbackEnabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "PressScale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.onSecondaryContainer
        else 
            MaterialTheme.colorScheme.onSurfaceVariant,
        label = "IconColor"
    )

    val indicatorColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        label = "IndicatorColor"
    )

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                if (hapticFeedbackEnabled && !selected) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // M3 Active Indicator Pill around the Icon
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(indicatorColor)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = stringResource(labelResId),
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(labelResId),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
