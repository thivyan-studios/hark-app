package com.thivyanstudios.hark.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// Overall footprint leaves room for the thumb to swell past the track when pressed
private val ContainerWidth = 62.dp
private val ContainerHeight = 38.dp
private val TrackWidth = 54.dp
private val TrackHeight = 28.dp
private val TrackInset = 2.dp

// Resting thumb sits inside the track; pressed thumb becomes a larger glass lens
private val ThumbRestWidth = 34.dp
private val ThumbRestHeight = 24.dp
private val ThumbPressedWidth = 46.dp
private val ThumbPressedHeight = 36.dp

/**
 * A "liquid glass" style switch. The thumb is a frosted pane that blurs the track
 * beneath it; while pressed it grows into a clear lens that overhangs the track.
 * On Android 11 and below (no real-time blur) the thumb falls back to solid white.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val isDark = isAppInDarkTheme()
    val hazeState = rememberHazeState()

    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "TrackColor",
    )

    val bouncy = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val thumbWidth by animateDpAsState(if (pressed) ThumbPressedWidth else ThumbRestWidth, bouncy, label = "ThumbW")
    val thumbHeight by animateDpAsState(if (pressed) ThumbPressedHeight else ThumbRestHeight, bouncy, label = "ThumbH")

    // Thumb centre slides between the two ends of the track
    val trackStart = (ContainerWidth - TrackWidth) / 2
    val restCentreOff = trackStart + TrackInset + ThumbRestWidth / 2
    val restCentreOn = trackStart + TrackWidth - TrackInset - ThumbRestWidth / 2
    val thumbCentre by animateDpAsState(if (checked) restCentreOn else restCentreOff, bouncy, label = "ThumbX")

    // How milky the glass is: frosted at rest, nearly clear while pressed
    val frost by animateFloatAsState(
        targetValue = if (pressed) 0.12f else if (isDark) 0.82f else 0.72f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "Frost",
    )

    val glassStyle = rememberThumbGlassStyle(blurRadius = 6.dp, noise = 0.04f) { frost }

    // Bright rim on top fading down, like light catching the edge of glass
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.25f), trackColor.copy(alpha = 0.5f))
    )

    Box(
        modifier = modifier
            .size(ContainerWidth, ContainerHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interactionSource,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Track: the blur source the thumb looks through
        Box(
            modifier = Modifier
                .offset(x = trackStart)
                .size(TrackWidth, TrackHeight)
                .clip(CircleShape)
                .hazeSource(hazeState)
                .background(trackColor)
        )

        // Glass thumb
        Box(
            modifier = Modifier
                .offset(x = thumbCentre - thumbWidth / 2)
                .size(thumbWidth, thumbHeight)
                .shadow(
                    elevation = if (pressed) 8.dp else 3.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.2f),
                    spotColor = Color.Black.copy(alpha = 0.3f),
                )
                .clip(CircleShape)
                .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
                .border(1.dp, rim, CircleShape)
        )
    }
}
