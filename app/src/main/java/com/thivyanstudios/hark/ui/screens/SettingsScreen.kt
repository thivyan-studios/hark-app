package com.thivyanstudios.hark.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.SquishyBox
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.util.Constants
import java.text.DecimalFormat
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val uiState by settingsViewModel.uiState.collectAsState()
    val df = remember { DecimalFormat("0.0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = uiState.versionName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                SettingsSwitchRow(
                    text = stringResource(R.string.settings_haptic_feedback),
                    checked = uiState.hapticFeedbackEnabled,
                    onCheckedChange = settingsViewModel::setHapticFeedbackEnabled,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )
                
                SettingsSwitchRow(
                    text = stringResource(R.string.settings_keep_screen_on),
                    checked = uiState.keepScreenOn,
                    onCheckedChange = settingsViewModel::setKeepScreenOn,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )
                
                SettingsSwitchRow(
                    text = stringResource(R.string.settings_disable_hearing_aid_priority),
                    checked = uiState.disableHearingAidPriority,
                    onCheckedChange = settingsViewModel::setDisableHearingAidPriority,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )

                SettingsSwitchRow(
                    text = stringResource(R.string.settings_noise_suppression),
                    checked = uiState.noiseSuppressionEnabled,
                    onCheckedChange = settingsViewModel::setNoiseSuppressionEnabled,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )

                SettingsSwitchRow(
                    text = stringResource(R.string.settings_dynamics_processing),
                    checked = uiState.dynamicsProcessingEnabled,
                    onCheckedChange = settingsViewModel::setDynamicsProcessingEnabled,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                val formattedGain = when {
                    uiState.microphoneGain > 0.01f -> "+${df.format(uiState.microphoneGain)}"
                    uiState.microphoneGain < -0.01f -> df.format(uiState.microphoneGain)
                    else -> "0"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_microphone_gain), modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Text(text = stringResource(R.string.gain_db_format, formattedGain))
                }
                Slider(
                    value = uiState.microphoneGain,
                    onValueChange = { settingsViewModel.setMicrophoneGain(it) },
                    valueRange = -10f..30f,
                    steps = 39,
                    onValueChangeFinished = {
                        if (uiState.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_equalizer),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp).align(Alignment.Start)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val frequencies = Constants.Preferences.EQUALIZER_FREQUENCIES
                    
                    frequencies.forEachIndexed { index, label ->
                        val bandValue = uiState.equalizerBands.getOrElse(index) { 0f }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${bandValue.roundToInt()}dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 0.dp)
                            )

                            // Vertical Slider implementation
                            // We use a custom layout modifier to properly measure and place the rotated slider
                            // This ensures the slider takes up the intended vertical space without weird padding issues
                            Slider(
                                value = bandValue,
                                onValueChange = { settingsViewModel.setEqualizerBand(index, it) },
                                valueRange = -10f..10f,
                                onValueChangeFinished = {
                                    if (uiState.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier
                                    .graphicsLayer {
                                        rotationZ = 270f
                                        transformOrigin = TransformOrigin.Center
                                    }
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(
                                            Constraints(
                                                minWidth = constraints.minHeight,
                                                maxWidth = constraints.maxHeight,
                                                minHeight = constraints.minWidth,
                                                maxHeight = constraints.maxWidth,
                                            )
                                        )
                                        layout(placeable.height, placeable.width) {
                                            placeable.place(
                                                -(placeable.width - placeable.height) / 2,
                                                -(placeable.height - placeable.width) / 2
                                            )
                                        }
                                    }
                                    .width(200.dp) // This sets the length of the vertical slider
                                    .height(50.dp) // This sets the touch target width of the vertical slider
                            )
                            
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 0.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SquishyBox(
                    onClick = {
                        settingsViewModel.toggleTestAudio()
                    },
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_test_audio),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        SquishyBox(
            onClick = {
                 uriHandler.openUri("https://ko-fi.com/thivyanstudios")
            },
            backgroundColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_support_kofi),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
