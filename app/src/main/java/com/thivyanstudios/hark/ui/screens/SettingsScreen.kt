package com.thivyanstudios.hark.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.theme.SquishyBox
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.util.Constants

import java.util.Locale

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val uiState by settingsViewModel.uiState.collectAsState()
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<com.thivyanstudios.hark.data.model.WhisperModel?>(null) }

    if (showDeleteDialog && modelToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                HarkText(
                    text = stringResource(R.string.model_delete_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                HarkText(
                    text = stringResource(R.string.model_delete_message, modelToDelete!!.name),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    settingsViewModel.deleteModel(modelToDelete!!.id)
                    showDeleteDialog = false
                }) {
                    HarkText(text = stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    HarkText(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { 
                HarkText(
                    text = stringResource(R.string.privacy_policy_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                ) 
            },
            text = {
                HarkText(
                    text = stringResource(R.string.privacy_policy_content),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                )
            },
            confirmButton = {
                ConfirmLogButton(
                    onConfirm = {
                        settingsViewModel.generateAndShareLog()
                        showPrivacyDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    HarkText(
                        text = stringResource(R.string.cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(100.dp))
            
            HarkText(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelLarge.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Section: Experience
            SettingsGroup(title = "Experience") {
                SettingsSwitchRow(
                    text = stringResource(R.string.settings_haptic_feedback),
                    icon = Icons.Default.Vibration,
                    checked = uiState.hapticFeedbackEnabled,
                    onCheckedChange = settingsViewModel::setHapticFeedbackEnabled,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )

                SettingsSwitchRow(
                    text = stringResource(R.string.settings_keep_screen_on),
                    icon = Icons.Default.Lightbulb,
                    checked = uiState.keepScreenOn,
                    onCheckedChange = settingsViewModel::setKeepScreenOn,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )
            }

            // Section: Audio
            SettingsGroup(title = "Audio Engine") {
                SettingsSwitchRow(
                    text = stringResource(R.string.settings_noise_suppression),
                    icon = Icons.Default.FilterList,
                    checked = uiState.noiseSuppressionEnabled,
                    onCheckedChange = settingsViewModel::setNoiseSuppressionEnabled,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                    enabled = uiState.isNoiseSuppressionSupported
                )

                SettingsSwitchRow(
                    text = stringResource(R.string.settings_dynamics_processing),
                    icon = Icons.Default.GraphicEq,
                    checked = uiState.dynamicsProcessingEnabled,
                    onCheckedChange = settingsViewModel::setDynamicsProcessingEnabled,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                    enabled = uiState.isDynamicsProcessingSupported
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Microphone Gain Slider
                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Mic, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            HarkText(text = stringResource(R.string.settings_microphone_gain))
                        }
                        HarkText(
                            text = stringResource(R.string.gain_db_format, formattedGain),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = Constants.Preferences.MIN_MIC_GAIN..Constants.Preferences.MAX_MIC_GAIN,
                        steps = Constants.Preferences.MIC_GAIN_STEPS,
                        onValueChangeFinished = {
                            settingsViewModel.setMicrophoneGain(sliderValue)
                            if (uiState.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                }
            }

            // Section: Connectivity
            SettingsGroup(title = "Connectivity") {
                SettingsSwitchRow(
                    text = stringResource(R.string.settings_enable_bluetooth_headset_support),
                    icon = Icons.Default.BluetoothAudio,
                    checked = uiState.enableBluetoothHeadsetSupport,
                    onCheckedChange = settingsViewModel::setEnableBluetoothHeadsetSupport,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )

                SettingsSwitchRow(
                    text = stringResource(R.string.settings_prefer_external_mic),
                    icon = Icons.Default.BluetoothAudio,
                    checked = uiState.preferExternalMic,
                    onCheckedChange = settingsViewModel::setPreferExternalMic,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                )

                SettingsSwitchRow(
                    text = stringResource(R.string.settings_bypass_bluetooth_checks),
                    icon = Icons.Default.Bluetooth,
                    checked = uiState.bypassBluetoothChecks,
                    onCheckedChange = settingsViewModel::setBypassBluetoothChecks,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                    enabled = uiState.isDeveloperOptionsEnabled
                )
            }

            // Section: Whisper AI
            SettingsGroup(title = "Whisper AI Engine") {
                // Available Models
                HarkText(
                    text = "Transcription Models",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp)
                )

                uiState.availableModels.forEach { model ->
                    WhisperModelItem(
                        model = model,
                        isSelected = uiState.selectedModelId == model.id,
                        isDownloaded = uiState.downloadedModelIds.contains(model.id),
                        downloadProgress = uiState.downloadProgress[model.id],
                        onSelect = { settingsViewModel.setSelectedModel(model.id) },
                        onDownload = { settingsViewModel.downloadModel(model.id) },
                        onDelete = {
                            modelToDelete = model
                            showDeleteDialog = true
                        },
                        hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                    )
                }

                if (uiState.downloadedModelIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    SettingsSwitchRow(
                        text = stringResource(R.string.settings_transcript_mode),
                        icon = Icons.Default.SpeakerNotesOff,
                        checked = uiState.transcriptModeEnabled,
                        onCheckedChange = settingsViewModel::setTranscriptModeEnabled,
                        hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Font Size Slider
                    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                        var sliderValue by remember(uiState.transcriptionFontSize) { mutableFloatStateOf(uiState.transcriptionFontSize) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.FormatSize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                HarkText(text = stringResource(R.string.settings_font_size))
                            }
                            HarkText(
                                text = "${sliderValue.toInt()} sp",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 14f..48f,
                            steps = 17,
                            onValueChangeFinished = {
                                settingsViewModel.setTranscriptionFontSize(sliderValue)
                                if (uiState.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Language Selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            HarkText(
                                text = "Model Language",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        
                        var expanded by remember { mutableStateOf(false) }
                        val languages = listOf("en" to "English", "auto" to "Auto-Detect")
                        
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                HarkText(
                                    text = languages.find { it.first == uiState.whisperLanguage }?.second ?: "English",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                languages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            settingsViewModel.setWhisperLanguage(code)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    SettingsSwitchRow(
                        text = "Translate to English",
                        icon = Icons.Default.Translate,
                        checked = uiState.whisperTranslate,
                        onCheckedChange = settingsViewModel::setWhisperTranslate,
                        hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                        enabled = uiState.whisperLanguage != "en"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Silence Threshold Slider
                    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                        var sliderValue by remember(uiState.silenceThreshold) { mutableFloatStateOf(uiState.silenceThreshold) }
                        // Map 0.0..0.1 to 0..100 for user display
                        val displayValue = (sliderValue * 1000).toInt() 
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.SurroundSound,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                HarkText(text = stringResource(R.string.settings_silence_threshold))
                            }
                            HarkText(
                                text = stringResource(R.string.sensitivity_format, displayValue),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 0.0001f..0.02f,
                            onValueChangeFinished = {
                                settingsViewModel.setSilenceThreshold(sliderValue)
                                if (uiState.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }

                    // Thread Count Slider
                    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                HarkText(text = "CPU Threads")
                            }
                            HarkText(
                                text = "${uiState.whisperThreads}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = uiState.whisperThreads.toFloat(),
                            onValueChange = { settingsViewModel.setWhisperThreads(it.toInt()) },
                            valueRange = 1f..8f,
                            steps = 6,
                            onValueChangeFinished = {
                                if (uiState.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Support Button - Larger and more rectangular
            SquishyBox(
                onClick = { showPrivacyDialog = true },
                modifier = Modifier
                    .width(260.dp)
                    .height(56.dp),
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                cornerRadius = 16.dp,
                hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    HarkText(
                        text = stringResource(R.string.settings_generate_log),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            HarkText(
                text = uiState.versionName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            
            Spacer(modifier = Modifier.height(180.dp)) // Leave room for navbar
        }

        // Fading Header Overlay to prevent UI from scrolling into status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )
    }
}

@Composable
fun ConfirmLogButton(onConfirm: () -> Unit) {
    TextButton(onClick = onConfirm) {
        HarkText(
            text = stringResource(R.string.accept),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        HarkText(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                content()
            }
        }
    }
}

private fun formatOneDecimal(value: Float): String {
    return String.format(Locale.US, "%.1f", value)
}
