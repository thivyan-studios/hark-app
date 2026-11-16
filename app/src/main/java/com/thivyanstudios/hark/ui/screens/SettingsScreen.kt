package com.thivyanstudios.hark.ui.screens

import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.data.model.SherpaModel
import com.thivyanstudios.hark.ui.theme.BetaBadge
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.util.Constants
import java.util.Locale

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onRequestBatteryOptimizationExemption: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(settingsViewModel)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(settingsViewModel)
        }
    }

    val haptic = LocalHapticFeedback.current
    val uiState by settingsViewModel.uiState.collectAsState()
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<SherpaModel?>(null) }

    if (showDeleteDialog && modelToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                HarkText(
                    text = stringResource(R.string.model_delete_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                HarkText(
                    text = stringResource(R.string.model_delete_message, stringResource(modelToDelete!!.nameRes)),
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
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { 
                HarkText(
                    text = stringResource(R.string.privacy_policy_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        )
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Clears the 32dp top fade so the first header isn't washed out
                Spacer(modifier = Modifier.height(36.dp))

                // Section: Experience
                SettingsGroup(title = stringResource(R.string.settings_section_experience)) {
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
                SettingsGroup(title = stringResource(R.string.settings_section_audio_engine)) {
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

                    SettingsSwitchRow(
                        text = stringResource(R.string.settings_treble_boost),
                        icon = Icons.Default.RecordVoiceOver,
                        checked = uiState.trebleBoostEnabled,
                        onCheckedChange = settingsViewModel::setTrebleBoostEnabled,
                        hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Microphone Gain Slider
                    var gainValue by remember(uiState.microphoneGain) { mutableFloatStateOf(uiState.microphoneGain) }
                    SettingsSliderRow(
                        icon = Icons.Default.Mic,
                        label = stringResource(R.string.settings_microphone_gain),
                        value = gainValue,
                        onValueChange = { gainValue = it },
                        onValueChangeFinished = { settingsViewModel.setMicrophoneGain(gainValue) },
                        valueRange = Constants.Preferences.MIN_MIC_GAIN..Constants.Preferences.MAX_MIC_GAIN,
                        valueLabel = { stringResource(R.string.gain_db_format, formatGain(it)) },
                        hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                        step = 1f,
                        origin = Constants.Preferences.DEFAULT_GAIN,
                        warnAbove = GAIN_WARNING_DB,
                        warningText = stringResource(R.string.gain_loud_warning),
                    )
                }

                // Section: Connectivity
                SettingsGroup(title = stringResource(R.string.settings_section_connectivity)) {
                    SettingsSwitchRow(
                        text = stringResource(R.string.settings_enable_bluetooth_headset_support),
                        icon = Icons.Default.BluetoothAudio,
                        checked = uiState.enableBluetoothHeadsetSupport,
                        onCheckedChange = settingsViewModel::setEnableBluetoothHeadsetSupport,
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

                    if (!uiState.isBatteryOptimizationIgnored) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onRequestBatteryOptimizationExemption() }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            HarkText(
                                text = stringResource(R.string.settings_disable_battery_optimization),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                // Section: Sherpa-ONNX STT Engine
                SettingsGroup(
                    title = stringResource(R.string.settings_section_stt_engine),
                    badge = { BetaBadge() }
                ) {
                    HarkText(
                        text = stringResource(R.string.settings_transcription_models),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 8.dp)
                    )

                    uiState.availableModels.forEach { model ->
                        SherpaModelItem(
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
                        val fontSizeFormat = stringResource(R.string.font_size_sp_format)
                        var fontSizeValue by remember(uiState.transcriptionFontSize) { mutableFloatStateOf(uiState.transcriptionFontSize) }
                        SettingsSliderRow(
                            icon = Icons.Default.FormatSize,
                            label = stringResource(R.string.settings_font_size),
                            value = fontSizeValue,
                            onValueChange = { fontSizeValue = it },
                            onValueChangeFinished = { settingsViewModel.setTranscriptionFontSize(fontSizeValue) },
                            valueRange = 14f..48f,
                            valueLabel = { fontSizeFormat.format(it.toInt()) },
                            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                            step = 2f,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Thread Count Slider
                        SettingsSliderRow(
                            icon = Icons.Default.Memory,
                            label = stringResource(R.string.settings_cpu_threads),
                            value = uiState.sttThreads.toFloat(),
                            onValueChange = { settingsViewModel.setSttThreads(it.toInt()) },
                            onValueChangeFinished = {},
                            valueRange = 1f..8f,
                            valueLabel = { "${it.toInt()}" },
                            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                            step = 1f,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // M3 Support Log Button
                FilledTonalButton(
                    onClick = {
                        if (uiState.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showPrivacyDialog = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(52.dp)
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_generate_log),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                HarkText(
                    text = uiState.versionName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )

                Spacer(modifier = Modifier.height(140.dp))
            }

            // Top Fading Edge Overlay for Settings Scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )

            // Bottom Fading Edge Overlay for Settings Scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .align(Alignment.BottomCenter)
            )
        }
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
    badge: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        ) {
            HarkText(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                badge()
            }
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
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

// Above this, extra mic gain risks distortion and uncomfortable loudness
private const val GAIN_WARNING_DB = 20f

private fun formatGain(value: Float): String = when {
    value > 0.01f -> "+${formatOneDecimal(value)}"
    value < -0.01f -> formatOneDecimal(value)
    else -> "0"
}
