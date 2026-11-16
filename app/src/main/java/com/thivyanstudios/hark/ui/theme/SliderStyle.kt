package com.thivyanstudios.hark.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Every visual setting for HARK's sliders, in one place. All sliders in the app
 * (via [GlassFader] and SettingsSliderRow) read from here, so changing a value
 * below restyles every slider at once.
 */
object HarkSliderStyle {

    // --- Size & layout ---------------------------------------------------------
    /** Total height of the slider (leaves room for the handle to swell). */
    val ContainerHeight = 44.dp
    /** Thickness of the pill-shaped track. */
    val TrackHeight = 14.dp
    /** Handle size at rest. */
    val ThumbRestWidth = 30.dp
    val ThumbRestHeight = 22.dp
    /** Handle size while being dragged (the "lens"). */
    val ThumbDragWidth = 46.dp
    val ThumbDragHeight = 36.dp
    /** Gap between the row's label and the slider below it. */
    val LabelToSliderGap = 8.dp
    /** Icon size in the row header. */
    val IconSize = 20.dp
    /** Gap between the icon and the label. */
    val IconToLabelGap = 12.dp

    // --- Glass handle ----------------------------------------------------------
    /** How much the handle blurs the track behind it. */
    val ThumbBlurRadius = 6.dp
    /** Film-grain amount on the glass (0 = none). */
    const val ThumbNoise = 0.04f
    /** How milky-white the handle is at rest (0 = clear, 1 = solid white). */
    const val ThumbFrostLight = 0.72f
    const val ThumbFrostDark = 0.82f
    /** How milky-white the handle is while dragging (low = clear lens). */
    const val ThumbFrostDragging = 0.12f
    /** Brightness of the glass rim at the top and middle of the handle. */
    const val RimTopAlpha = 0.95f
    const val RimMidAlpha = 0.25f
    /** Shadow under the handle. */
    val ThumbShadowRest = 3.dp
    val ThumbShadowDragging = 8.dp

    // --- Track & fill ----------------------------------------------------------
    /** Fill opacity when the value is below the origin (e.g. negative gain). */
    const val BelowOriginFillAlpha = 0.6f
    /** The small notch marking the origin (e.g. 0 dB). */
    val NotchWidth = 2.dp
    val NotchInset = 6.dp
    const val NotchAlpha = 0.35f

    // --- Warning zone (e.g. gain too high) -------------------------------------
    val WarningStartColor = Color(0xFFFFB300) // amber
    val WarningEndColor = Color(0xFFFF5449)   // red

    // --- Value bubble ------------------------------------------------------------
    val BubbleCornerRadius = 12.dp
    val BubbleHorizontalPadding = 10.dp
    val BubbleVerticalPadding = 4.dp
    val BubbleShadow = 4.dp
    /** Gap between the bubble and the top of the dragged handle. */
    val BubbleGap = 6.dp

    // --- Motion --------------------------------------------------------------------
    const val SpringDamping = Spring.DampingRatioMediumBouncy
    const val SpringStiffness = Spring.StiffnessMediumLow
    /** Values within this many steps of the origin snap to it. */
    const val OriginSnapSteps = 1.5f
}
