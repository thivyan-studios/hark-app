package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
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
            icon = { Icon(painterResource(id = R.drawable.ic_home), contentDescription = stringResource(R.string.nav_home)) },
            label = { Text(stringResource(R.string.nav_home)) },
            selected = currentRoute == Navigation.ROUTE_HOME,
            onClick = { onNavigate(Navigation.ROUTE_HOME) },
            hapticFeedbackEnabled = hapticFeedbackEnabled
        )
        AnimatedNavigationBarItem(
            icon = { Icon(painterResource(id = R.drawable.ic_settings), contentDescription = stringResource(R.string.nav_settings)) },
            label = { Text(stringResource(R.string.nav_settings)) },
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
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = NavigationBarItemDefaults.colors(),
    hapticFeedbackEnabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = ""
    )
    val haptic = LocalHapticFeedback.current

    NavigationBarItem(
        selected = selected,
        onClick = {
            if (hapticFeedbackEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onClick()
        },
        icon = { Box(modifier = Modifier.scale(scale)) { icon() } },
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { Box(modifier = Modifier.scale(scale)) { it() } } },
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        interactionSource = interactionSource
    )
}
