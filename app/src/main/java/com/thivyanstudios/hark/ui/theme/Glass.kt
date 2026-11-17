package com.thivyanstudios.hark.ui.theme

import androidx.compose.ui.util.lerp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

// The app's "liquid glass" look in one place, so every glass surface matches and a tweak here
// changes them all. There are two kinds:
//  - panel glass: frosted and tinted, for things content scrolls behind (navbar, action pill, cards)
//  - thumb glass: clear and bright, for small moving parts (switch and slider thumbs)

/**
 * How see-through the glass is, from the Glass Translucency setting: 1 = the full glass look,
 * 0 = solid. Provided once at the top of the app, so every glass surface follows it.
 */
val LocalGlassTranslucency = compositionLocalOf { 1f }

/**
 * Scales a glass tint's opacity with the Glass Translucency setting: at full translucency
 * it's [glassAlpha] (the designed look), and it rises to [solidAlpha] as translucency drops.
 */
@Composable
fun glassAlpha(glassAlpha: Float, solidAlpha: Float = 0.97f): Float =
    lerp(solidAlpha, glassAlpha, LocalGlassTranslucency.current.coerceIn(0f, 1f))

/**
 * True when the app is showing its dark theme. Reads the theme's real colours rather than the
 * system setting, so it stays correct if the app ever gets its own light/dark choice.
 */
@Composable
fun isAppInDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Bright-at-the-top rim used on glass panels, so their edges catch the light consistently. */
@Composable
fun glassRim(): Brush {
    val isDark = isAppInDarkTheme()
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.22f else 0.6f),
            Color.White.copy(alpha = 0.04f),
        )
    )
}

/**
 * Frosted panel glass: blurs whatever is behind it, then washes it with the surface colour so
 * text and icons on top stay readable. Android 12+ gets the real blur; older versions fall
 * back to a near-solid tint.
 */
@Composable
fun rememberPanelGlassStyle(blurRadius: Dp = 24.dp): HazeBlurStyle {
    val isDark = isAppInDarkTheme()
    val surface = MaterialTheme.colorScheme.surface
    val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val tintAlpha = glassAlpha(if (isDark) 0.55f else 0.45f)
    return remember(surface, fallback, tintAlpha, blurRadius) {
        HazeBlurStyle {
            blurRadius(blurRadius)
            backgroundColor(surface)
            colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = tintAlpha))))
            noiseFactor(0.06f)
            fallbackColorEffect(HazeColorEffect.tint(fallback.copy(alpha = 0.94f)))
        }
    }
}

/**
 * Clear thumb glass: a light blur with a white "frost" over it. [frost] is how milky the glass
 * is (0 = clear, 1 = solid white). It's read while drawing, so it can animate (e.g. clearing
 * while the thumb is pressed) without rebuilding the style.
 */
@Composable
fun rememberThumbGlassStyle(blurRadius: Dp, noise: Float, frost: () -> Float): HazeBlurStyle {
    // Less translucency makes the thumb milkier, up to solid white
    val translucency = LocalGlassTranslucency.current.coerceIn(0f, 1f)
    return remember(blurRadius, noise, translucency) {
        HazeBlurStyle {
            blurRadius(blurRadius)
            noiseFactor(noise)
            colorEffects(listOf(HazeColorEffect.tint(Color.White.copy(alpha = lerp(1f, frost(), translucency)))))
            fallbackColorEffect(HazeColorEffect.tint(Color.White))
        }
    }
}
