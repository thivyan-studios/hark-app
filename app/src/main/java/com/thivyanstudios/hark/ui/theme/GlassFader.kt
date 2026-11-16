package com.thivyanstudios.hark.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val S = HarkSliderStyle

/**
 * Colour for a value on the fader: the theme colour normally, dimmed below [origin],
 * shifting amber → red once it passes [warnAbove] (for "this may be too loud" ranges).
 */
fun faderZoneColor(
    value: Float,
    base: Color,
    origin: Float,
    max: Float,
    warnAbove: Float?,
): Color = when {
    warnAbove != null && value > warnAbove ->
        lerp(S.WarningStartColor, S.WarningEndColor, ((value - warnAbove) / (max - warnAbove)).coerceIn(0f, 1f))
    value < origin -> base.copy(alpha = S.BelowOriginFillAlpha)
    else -> base
}

/**
 * A "liquid glass" slider that matches [GlassSwitch]. All of its looks come from
 * [HarkSliderStyle].
 *
 * The fill grows outward from [origin] (defaults to the start of the range, i.e. a normal
 * left-to-right fill). When [origin] is inside the range (e.g. 0 dB on a −10…+30 dB gain
 * slider) a notch marks it, nearby values snap to it with a firmer haptic click, and
 * double-tapping resets to it. [step] = null makes the slider continuous.
 */
@Composable
fun GlassFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: @Composable (Float) -> String,
    modifier: Modifier = Modifier,
    origin: Float? = null,
    step: Float? = null,
    warnAbove: Float? = null,
    hapticFeedbackEnabled: Boolean = true,
    onDraggingChange: (Boolean) -> Unit = {},
) {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val originValue = origin ?: start
    val hasCentreOrigin = originValue > start && originValue < end

    fun fractionOf(v: Float) = ((v - start) / (end - start)).coerceIn(0f, 1f)
    fun valueOf(f: Float) = start + f * (end - start)
    fun stepped(v: Float): Float {
        if (step == null) return v.coerceIn(start, end)
        if (hasCentreOrigin && abs(v - originValue) < step * S.OriginSnapSteps) return originValue
        return (((v - start) / step).roundToInt() * step + start).coerceIn(start, end)
    }

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val hazeState = rememberHazeState()

    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val hapticsOn by rememberUpdatedState(hapticFeedbackEnabled)

    fun emit(newValue: Float) {
        if (newValue == currentValue) return
        currentOnChange(newValue)
        // Ticks only make sense for stepped sliders; continuous ones would buzz constantly
        if (hapticsOn && step != null) {
            haptic.performHapticFeedback(
                if (hasCentreOrigin && newValue == originValue) HapticFeedbackType.LongPress
                else HapticFeedbackType.SegmentFrequentTick
            )
        }
    }

    val bouncyFloat = spring<Float>(dampingRatio = S.SpringDamping, stiffness = S.SpringStiffness)
    val bouncyDp = spring<Dp>(dampingRatio = S.SpringDamping, stiffness = S.SpringStiffness)

    // Thumb position: follows the finger exactly while dragging, springs otherwise
    val settled = remember { Animatable(fractionOf(value)) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fractionOf(value)) }
    LaunchedEffect(value) {
        if (!dragging) settled.animateTo(fractionOf(value), bouncyFloat)
    }
    val shownFraction = if (dragging) dragFraction else settled.value
    val currentOnDraggingChange by rememberUpdatedState(onDraggingChange)
    LaunchedEffect(dragging) { currentOnDraggingChange(dragging) }

    val thumbWidth by animateDpAsState(if (dragging) S.ThumbDragWidth else S.ThumbRestWidth, bouncyDp, label = "ThumbW")
    val thumbHeight by animateDpAsState(if (dragging) S.ThumbDragHeight else S.ThumbRestHeight, bouncyDp, label = "ThumbH")
    val bubble by animateFloatAsState(if (dragging) 1f else 0f, spring(stiffness = S.SpringStiffness * 4), label = "Bubble")
    val frost by animateFloatAsState(
        targetValue = if (dragging) S.ThumbFrostDragging else if (isDark) S.ThumbFrostDark else S.ThumbFrostLight,
        animationSpec = spring(stiffness = S.SpringStiffness),
        label = "Frost",
    )

    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val notchColor = MaterialTheme.colorScheme.onSurface.copy(alpha = S.NotchAlpha)
    val fillColor = faderZoneColor(valueOf(shownFraction), primary, originValue, end, warnAbove)

    val glassStyle = remember(isDark) {
        HazeBlurStyle {
            blurRadius(S.ThumbBlurRadius)
            noiseFactor(S.ThumbNoise)
            colorEffects(listOf(HazeColorEffect.tint(Color.White.copy(alpha = frost))))
            fallbackColorEffect(HazeColorEffect.tint(Color.White))
        }
    }
    val rim = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = S.RimTopAlpha), Color.White.copy(alpha = S.RimMidAlpha), fillColor.copy(alpha = 0.5f))
    )
    val bubbleShape = RoundedCornerShape(S.BubbleCornerRadius)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(S.ContainerHeight)
            .semantics {
                val stepCount = if (step == null) 0 else ((end - start) / step).roundToInt() - 1
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, stepCount)
                setProgress { target ->
                    emit(stepped(target))
                    currentOnFinished()
                    true
                }
            }
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val thumbRestPx = with(density) { S.ThumbRestWidth.toPx() }
        val travel = (widthPx - thumbRestPx).coerceAtLeast(1f)
        fun centreX(f: Float) = thumbRestPx / 2 + f * travel
        fun fractionAt(x: Float) = ((x - thumbRestPx / 2) / travel).coerceIn(0f, 1f)

        val gestures = Modifier
            .pointerInput(travel) {
                detectTapGestures(
                    onDoubleTap = if (hasCentreOrigin) {
                        {
                            emit(originValue)
                            currentOnFinished()
                        }
                    } else null,
                    onTap = { pos ->
                        emit(stepped(valueOf(fractionAt(pos.x))))
                        currentOnFinished()
                    },
                )
            }
            .pointerInput(travel) {
                fun finish() {
                    scope.launch {
                        settled.snapTo(dragFraction)
                        dragging = false
                        settled.animateTo(fractionOf(currentValue), bouncyFloat)
                    }
                    currentOnFinished()
                }
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        dragFraction = fractionAt(pos.x)
                        dragging = true
                        emit(stepped(valueOf(dragFraction)))
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        dragFraction = (dragFraction + dx / travel).coerceIn(0f, 1f)
                        emit(stepped(valueOf(dragFraction)))
                    },
                )
            }

        // Track: pill with the fill growing out from the origin. Also the blur source.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(S.TrackHeight)
                .clip(CircleShape)
                .hazeSource(hazeState)
                .drawBehind {
                    drawRect(trackColor)
                    val x0 = if (hasCentreOrigin) centreX(fractionOf(originValue)) else 0f
                    val x1 = centreX(shownFraction)
                    val left = min(x0, x1)
                    val right = max(x0, x1)
                    if (right - left > 0.5f) {
                        drawRoundRect(
                            color = fillColor,
                            topLeft = Offset(left - size.height / 2, 0f),
                            size = Size(right - left + size.height, size.height),
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
                    if (hasCentreOrigin) {
                        val notchW = S.NotchWidth.toPx()
                        val notchH = size.height - S.NotchInset.toPx()
                        drawRoundRect(
                            color = notchColor,
                            topLeft = Offset(x0 - notchW / 2, (size.height - notchH) / 2),
                            size = Size(notchW, notchH),
                            cornerRadius = CornerRadius(notchW / 2),
                        )
                    }
                }
        )

        // Glass handle
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (centreX(shownFraction) - thumbWidth.toPx() / 2).roundToInt(),
                        ((S.ContainerHeight - thumbHeight) / 2).roundToPx(),
                    )
                }
                .size(thumbWidth, thumbHeight)
                .shadow(
                    elevation = if (dragging) S.ThumbShadowDragging else S.ThumbShadowRest,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = 0.2f),
                    spotColor = Color.Black.copy(alpha = 0.3f),
                )
                .clip(CircleShape)
                .hazeBlur(input = HazeInput.Sources(hazeState), style = glassStyle)
                .border(1.dp, rim, CircleShape)
        )

        // Value bubble above the handle while dragging
        if (bubble > 0.01f) {
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) {
                            p.place(
                                (centreX(shownFraction) - p.width / 2f).roundToInt(),
                                ((S.ContainerHeight - S.ThumbDragHeight) / 2).roundToPx() - p.height - S.BubbleGap.roundToPx(),
                            )
                        }
                    }
                    .graphicsLayer {
                        alpha = bubble
                        scaleX = 0.8f + 0.2f * bubble
                        scaleY = 0.8f + 0.2f * bubble
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .shadow(S.BubbleShadow, bubbleShape)
                    .background(MaterialTheme.colorScheme.inverseSurface, bubbleShape)
                    .padding(horizontal = S.BubbleHorizontalPadding, vertical = S.BubbleVerticalPadding)
            ) {
                Text(
                    text = valueLabel(value),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }

        // Gesture layer on top so taps anywhere on the fader (incl. the handle) register
        Box(Modifier.matchParentSize().then(gestures))
    }
}

