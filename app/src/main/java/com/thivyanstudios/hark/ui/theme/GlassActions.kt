package com.thivyanstudios.hark.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Lets a dialog ask for the whole app behind it to be blurred (true while it's open).
 * Dialogs are separate windows and many devices disable cross-window blur, so the app
 * blurs its own content instead, which works on every Android 12+ device.
 */
val LocalBackdropBlur = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * Frosted-glass surface. With a [hazeState] it blurs whatever that state's source draws
 * behind it (like the navbar); without one it's a translucent tinted pane, for places where
 * nothing scrolls behind it.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    hazeState: HazeState? = null,
    elevation: Dp = 14.dp,
    paneAlpha: Float? = null,
): Modifier {
    val isDark = isAppInDarkTheme()
    val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val style = rememberPanelGlassStyle()
    val base = this
        .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
        .clip(shape)
    val filled = if (hazeState != null) {
        base.hazeBlur(input = HazeInput.Sources(hazeState), style = style)
    } else {
        base.background(fallback.copy(alpha = glassAlpha(paneAlpha ?: if (isDark) 0.72f else 0.85f, solidAlpha = 1f)))
    }
    return filled.border(1.dp, glassRim(), shape)
}

/** A floating glass pill holding [GlassActionButton]s, e.g. Copy / Share / Clear. */
@Composable
fun GlassActionBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .glassSurface(RoundedCornerShape(28.dp), hazeState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A filled icon in a soft pill of its own colour (like the navbar's selected tab), with a
 * small label underneath and a gentle press "squish". Use the Icons.Rounded set to match
 * the navbar.
 */
@Composable
fun GlassActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    hapticFeedbackEnabled: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "GlassActionPress",
    )
    val shownTint = if (enabled) tint else tint.copy(alpha = 0.38f)

    Column(
        modifier = modifier
            .widthIn(min = 64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(shownTint.copy(alpha = 0.18f))
                .padding(horizontal = 16.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = shownTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
