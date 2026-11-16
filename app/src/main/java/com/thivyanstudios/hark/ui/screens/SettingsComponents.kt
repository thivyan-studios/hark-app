package com.thivyanstudios.hark.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.thivyanstudios.hark.ui.theme.GlassFader
import com.thivyanstudios.hark.ui.theme.GlassSwitch
import com.thivyanstudios.hark.ui.theme.HarkSliderStyle
import com.thivyanstudios.hark.ui.theme.faderZoneColor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.thivyanstudios.hark.ui.theme.HarkText

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.thivyanstudios.hark.data.model.SherpaModel

@Composable
fun SherpaModelItem(
    model: SherpaModel,
    isSelected: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    hapticFeedbackEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .alpha(if (isSelected || isDownloaded) 1f else 0.7f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            HarkText(
                text = model.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
            HarkText(
                text = model.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            if (downloadProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        if (isDownloaded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!model.isAsset && !isSelected) {
                    IconButton(
                        onClick = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete model",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                IconButton(
                    onClick = {
                        if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect()
                    }
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = if (isSelected) "Selected" else "Switch to this model",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else if (downloadProgress == null) {
            IconButton(
                onClick = {
                    if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDownload()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download model",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SettingsSwitchRow(
    text: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hapticFeedbackEnabled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        HarkText(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
        GlassSwitch(
            checked = checked,
            onCheckedChange = {
                if (hapticFeedbackEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onCheckedChange(it)
            },
            enabled = enabled,
        )
    }
}

/**
 * A settings row with an icon, label and current value, and a [GlassFader] below.
 * Every slider in Settings uses this, and every slider's looks come from
 * [HarkSliderStyle], so changing either restyles all of them together.
 *
 * @param valueLabel Formats a value for the header and the drag bubble, e.g. "+3 dB".
 * @param step Distance between allowed values, or null for a smooth slider.
 * @param origin Where the fill starts from (null = left end). Use e.g. 0 for a ±dB slider.
 * @param warnAbove Values above this turn amber→red and show [warningText].
 */
@Composable
fun SettingsSliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: @Composable (Float) -> String,
    hapticFeedbackEnabled: Boolean,
    modifier: Modifier = Modifier,
    step: Float? = null,
    origin: Float? = null,
    warnAbove: Float? = null,
    warningText: String? = null,
) {
    // The fader's bubble shows the value while dragging, so fade the header copy out
    var dragging by remember { mutableStateOf(false) }
    val headerValueAlpha by animateFloatAsState(if (dragging) 0f else 1f, label = "SliderHeaderAlpha")
    val zoneColor = faderZoneColor(
        value = value,
        base = MaterialTheme.colorScheme.primary,
        origin = origin ?: valueRange.start,
        max = valueRange.endInclusive,
        warnAbove = warnAbove,
    )

    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(HarkSliderStyle.IconSize)
                )
                Spacer(modifier = Modifier.width(HarkSliderStyle.IconToLabelGap))
                HarkText(text = label)
            }
            HarkText(
                text = valueLabel(value),
                style = MaterialTheme.typography.labelLarge,
                color = zoneColor,
                modifier = Modifier.alpha(headerValueAlpha)
            )
        }
        Spacer(modifier = Modifier.height(HarkSliderStyle.LabelToSliderGap))
        GlassFader(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            valueLabel = valueLabel,
            origin = origin,
            step = step,
            warnAbove = warnAbove,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            onDraggingChange = { dragging = it },
        )
        if (warningText != null && warnAbove != null && value > warnAbove) {
            HarkText(
                text = warningText,
                style = MaterialTheme.typography.bodySmall,
                color = zoneColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
