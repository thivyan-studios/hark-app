package com.thivyanstudios.hark.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.SquishyBox
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.util.Constants
import kotlin.math.abs
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
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                    enabled = uiState.isNoiseSuppressionSupported
                )

                SettingsSwitchRow(
                    text = stringResource(R.string.settings_dynamics_processing),
                    checked = uiState.dynamicsProcessingEnabled,
                    onCheckedChange = settingsViewModel::setDynamicsProcessingEnabled,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                    enabled = uiState.isDynamicsProcessingSupported
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
                var sliderValue by remember(uiState.microphoneGain) { mutableFloatStateOf(uiState.microphoneGain) }
                
                val formattedGain = when {
                    sliderValue > 0.01f -> "+${formatOneDecimal(sliderValue)}"
                    sliderValue < -0.01f -> formatOneDecimal(sliderValue)
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
                    value = sliderValue,
                    onValueChange = { 
                        sliderValue = it
                    },
                    valueRange = Constants.Preferences.MIN_MIC_GAIN..Constants.Preferences.MAX_MIC_GAIN,
                    steps = Constants.Preferences.MIC_GAIN_STEPS,
                    onValueChangeFinished = {
                        settingsViewModel.setMicrophoneGain(sliderValue)
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
                        var localBandValue by remember(bandValue) { mutableFloatStateOf(bandValue) }

                        VerticalEqualizerBand(
                            frequencyLabel = label,
                            gain = localBandValue,
                            onGainChange = { 
                                localBandValue = it
                            },
                            onGainChangeFinished = {
                                settingsViewModel.setEqualizerBand(index, localBandValue)
                            },
                            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SquishyBox(
                    onClick = {
                        settingsViewModel.toggleTestAudio()
                    },
                    backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled // Internalized haptic call
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
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled // Internalized haptic call
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

private fun formatOneDecimal(value: Float): String {
    val rounded = (value * 10).roundToInt()
    val absRounded = abs(rounded)
    val integerPart = absRounded / 10
    val decimalPart = absRounded % 10
    val sign = if (rounded < 0) "-" else ""
    return "$sign$integerPart.$decimalPart"
}
