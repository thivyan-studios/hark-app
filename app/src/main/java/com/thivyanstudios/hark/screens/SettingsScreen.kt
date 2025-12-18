package com.thivyanstudios.hark.screens

import android.annotation.SuppressLint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.viewmodel.SettingsViewModel
import java.text.DecimalFormat

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    innerPadding: PaddingValues
) {
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val versionName by settingsViewModel.versionName.collectAsState()
    val hapticFeedbackEnabled by settingsViewModel.hapticFeedbackEnabled.collectAsState()
    val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
    val keepScreenOn by settingsViewModel.keepScreenOn.collectAsState()
    val disableHearingAidPriority by settingsViewModel.disableHearingAidPriority.collectAsState()
    val microphoneGain by settingsViewModel.microphoneGain.collectAsState()
    val df = remember { DecimalFormat("0.0") }

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = ""
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = versionName,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_haptic_feedback), modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            settingsViewModel.setHapticFeedbackEnabled(it)
                        }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_dark_mode), modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            settingsViewModel.setIsDarkMode(it)
                        }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_keep_screen_on), modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            settingsViewModel.setKeepScreenOn(it)
                        }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.settings_disable_hearing_aid_priority), modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = disableHearingAidPriority,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            settingsViewModel.setDisableHearingAidPriority(it)
                        }
                    )
                }
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
                    microphoneGain > 0.01f -> "+${df.format(microphoneGain)}"
                    microphoneGain < -0.01f -> df.format(microphoneGain)
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
                    value = microphoneGain,
                    onValueChange = { settingsViewModel.setMicrophoneGain(it) },
                    valueRange = -10f..30f,
                    steps = 39,
                    onValueChangeFinished = {
                        if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(100))
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = {
                            if (hapticFeedbackEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            uriHandler.openUri("https://ko-fi.com/thivyanstudios")
                        }
                    )
                }
        ) {
            Text(
                text = stringResource(R.string.settings_support_kofi),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}
