package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.HarkText
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
            .padding(bottom = 24.dp + navigationBarsPadding, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "PressScale"
    )

    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "SelectedScale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.primary
        else 
            MaterialTheme.colorScheme.onSurfaceVariant,
        label = "IconColor"
    )

    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelResId),
                tint = iconColor,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = pressScale * selectedScale
                        scaleY = pressScale * selectedScale
                    }
            )
            Spacer(modifier = Modifier.height(2.dp))
            HarkText(
                text = stringResource(labelResId),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = iconColor,
                modifier = Modifier.graphicsLayer {
                    alpha = if (selected) 1f else 0.8f
                    scaleX = pressScale
                    scaleY = scaleX
                }
            )
        }
    }
}
