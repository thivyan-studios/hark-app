package com.thivyanstudios.hark.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thivyanstudios.hark.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.viewmodel.SettingsViewModelFactory
import java.text.DecimalFormat

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    factory: SettingsViewModelFactory,
    innerPadding: PaddingValues
) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val disableHearingAidPriority by viewModel.disableHearingAidPriority.collectAsState()
    val microphoneGain by viewModel.microphoneGain.collectAsState()
    val bluetoothDevices by viewModel.bluetoothDevices.collectAsState()
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
                    Text(text = "Haptic feedback", modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setHapticFeedbackEnabled(it)
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
                    Text(text = "Use dark mode", modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setIsDarkMode(it)
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
                    Text(text = "Keep screen on when in foreground", modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setKeepScreenOn(it)
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
                    Text(text = "Disable Hearing Aid priority", modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Switch(
                        checked = disableHearingAidPriority,
                        onCheckedChange = {
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setDisableHearingAidPriority(it)
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
                    Text(text = "Microphone gain", modifier = Modifier.weight(1f).padding(end = 16.dp))
                    Text(text = "$formattedGain dB")
                }
                Slider(
                    value = microphoneGain,
                    onValueChange = { viewModel.setMicrophoneGain(it) },
                    valueRange = -10f..30f,
                    steps = 39,
                    onValueChangeFinished = {
                        if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                modifier = Modifier.padding(16.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }

                LaunchedEffect(bluetoothDevices) {
                    if (selectedDevice == null && bluetoothDevices.isNotEmpty()) {
                        selectedDevice = bluetoothDevices.first()
                    }
                }

                Text("Output Device", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        if (bluetoothDevices.isNotEmpty()) {
                            expanded = !expanded
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = selectedDevice?.name ?: if (bluetoothDevices.isEmpty()) "No devices found" else "",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        enabled = bluetoothDevices.isNotEmpty(),
                        shape = RectangleShape
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.clip(RectangleShape)
                    ) {
                        bluetoothDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name ?: "Unknown Device") },
                                onClick = {
                                    selectedDevice = device
                                    expanded = false
                                }
                            )
                        }
                    }
                }
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
                text = "Support me on Ko-fi ☕",
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}
