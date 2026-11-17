package com.thivyanstudios.hark.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A big "liquid glass" button: a glowing [color] core sits under a thick pane of frosted
 * glass, so the colour bleeds softly through it. Anything in [backdrop] (e.g. pulse rings)
 * is drawn sharp around the button and blurred where the glass covers it.
 *
 * The button's backdrop is entirely its own content, so instead of sampling the screen
 * behind it (like the navbar does) the glass draws its own blurred copy of that content.
 * Blur needs Android 12+; on older versions the core simply shows through un-blurred.
 */
@Composable
fun GlassOrbButton(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    cornerRadius: Dp = 46.dp,
    enabled: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    backdrop: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(cornerRadius)

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "OrbPress",
    )
    // The core swells on press so the glow "pushes" into the glass
    val coreScale by animateFloatAsState(
        targetValue = if (pressed) 0.8f else 0.62f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "OrbCore",
    )

    val isDark = isAppInDarkTheme()
    // Dark: smoky, faintly tinted glass. Light: frosted milky glass with the colour glowing through
    val glassBase = if (isDark) lerp(Color.Black, color, 0.14f) else lerp(Color.White, color, 0.16f)
    val sheenTop = if (isDark) 0.22f else 0.55f
    // White-on-white edges vanish in light mode, so there the rim is tinted with the colour
    // all the way round (lightest at the top, where the light catches it) to outline the glass
    val rimBrush = if (isDark) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.1f), color.copy(alpha = 0.7f)))
    } else {
        Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.45f), color.copy(alpha = 0.8f)))
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Sharp copy of the backdrop, visible only around the outside of the glass
        Box(Modifier.size(size * 1.8f), contentAlignment = Alignment.Center) {
            backdrop()
        }

        // The glass pane
        Box(
            modifier = Modifier
                .size(size * pressScale)
                .shadow(
                    elevation = 28.dp,
                    shape = shape,
                    ambientColor = if (isDark) color else color.copy(alpha = 0.8f),
                    spotColor = if (isDark) color else color.copy(alpha = 0.8f),
                )
                .clip(shape)
                .background(glassBase)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                ) {
                    if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            // What you see "through" the glass: backdrop + glowing core, heavily blurred
            Box(
                modifier = Modifier
                    .size(size * 1.8f)
                    .blur(26.dp, BlurredEdgeTreatment.Unbounded),
                contentAlignment = Alignment.Center,
            ) {
                backdrop()
                Box(
                    Modifier
                        .size(size * coreScale)
                        .background(color, RoundedCornerShape(cornerRadius * coreScale))
                )
            }

            // Glass Translucency setting: a layer of the glass's own colour over the glow.
            // Invisible at 100% (the full look); thicker as it drops, until at 0% the glass is
            // fully opaque and the glow is hidden. The coloured rim and shadow still show red/green.
            Box(
                Modifier
                    .matchParentSize()
                    .background(glassBase.copy(alpha = glassAlpha(glassAlpha = 0f, solidAlpha = 1f)))
            )

            // Specular sheen across the top of the glass
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = sheenTop),
                            0.45f to Color.White.copy(alpha = 0.03f),
                            1f to Color.Transparent,
                        )
                    )
            )

            // Glass rim: catches the light at the top, tinted at the bottom
            Box(
                Modifier
                    .matchParentSize()
                    .border(width = if (isDark) 1.5.dp else 2.dp, brush = rimBrush, shape = shape)
            )
            if (!isDark) {
                // Thin bright inner edge just inside the rim, so it still reads as glass
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(2.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f))),
                            shape = RoundedCornerShape(cornerRadius - 2.dp),
                        )
                )
            }

            // White over the glow. As the glass turns opaque and hides the glow, the icon takes
            // on the button's own colour instead (red or green), in both light and dark mode,
            // so the button's state stays obvious at any Glass Translucency setting.
            val contentColor = lerp(color, Color.White, LocalGlassTranslucency.current.coerceIn(0f, 1f))
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }
}
