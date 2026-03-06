package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.util.Constants.Navigation

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    hapticFeedbackEnabled: Boolean
) {
    NavigationBar {
        AnimatedNavigationBarItem(
            iconResId = R.drawable.ic_home,
            labelResId = R.string.nav_home,
            selected = currentRoute == Navigation.ROUTE_HOME,
            onClick = { onNavigate(Navigation.ROUTE_HOME) },
            hapticFeedbackEnabled = hapticFeedbackEnabled
        )
        AnimatedNavigationBarItem(
            iconResId = R.drawable.ic_settings,
            labelResId = R.string.nav_settings,
            selected = currentRoute == Navigation.ROUTE_SETTINGS,
            onClick = { onNavigate(Navigation.ROUTE_SETTINGS) },
            hapticFeedbackEnabled = hapticFeedbackEnabled
        )
    }
}

@Composable
fun RowScope.AnimatedNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconResId: Int,
    labelResId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    hapticFeedbackEnabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Animation for the "Pressed" state (Squishy feel)
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "PressScale"
    )

    // Animation for the "Selected" state (M3E Pop-out feel)
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "SelectedScale"
    )

    // Smooth color transition for the icon
    val iconColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.onSecondaryContainer 
        else 
            MaterialTheme.colorScheme.onSurfaceVariant,
        label = "IconColor"
    )

    val haptic = LocalHapticFeedback.current

    NavigationBarItem(
        selected = selected,
        onClick = {
            if (hapticFeedbackEnabled && !selected) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick()
        },
        icon = {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = stringResource(labelResId),
                tint = iconColor,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pressScale * selectedScale
                        scaleY = pressScale * selectedScale
                    }
            )
        },
        label = {
            Text(
                text = stringResource(labelResId),
                modifier = Modifier.graphicsLayer {
                    alpha = if (selected) 1f else 0.8f
                    scaleX = pressScale
                    scaleY = pressScale
                }
            )
        },
        modifier = modifier,
        enabled = enabled,
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        interactionSource = interactionSource
    )
}
