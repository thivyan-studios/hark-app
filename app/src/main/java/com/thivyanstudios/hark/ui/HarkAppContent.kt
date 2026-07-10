package com.thivyanstudios.hark.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.ui.screens.BottomNavBar
import com.thivyanstudios.hark.ui.screens.HistoryScreen
import com.thivyanstudios.hark.ui.screens.HomeScreen
import com.thivyanstudios.hark.ui.screens.SettingsScreen
import com.thivyanstudios.hark.ui.screens.TranscribeScreen
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.theme.HarkToast
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.util.Constants.Navigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarkAppContent(
    uiState: MainUiState,
    snackbarHostState: SnackbarHostState,
    mainViewModel: com.thivyanstudios.hark.ui.viewmodel.MainViewModel,
    settingsViewModel: SettingsViewModel,
    onToggleStreaming: () -> Unit,
    onClearTranscription: () -> Unit,
    onShareTranscription: (String) -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route ?: Navigation.ROUTE_HOME
    
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    if (uiState.shouldShowBatteryOptimizationPrompt) {
        AlertDialog(
            onDismissRequest = { mainViewModel.setBatteryOptimizationPromptShown(true) },
            title = {
                HarkText(
                    text = stringResource(R.string.battery_optimization_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                HarkText(
                    text = stringResource(R.string.battery_optimization_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mainViewModel.setBatteryOptimizationPromptShown(true)
                        onRequestBatteryOptimizationExemption()
                    }
                ) {
                    HarkText(
                        text = stringResource(R.string.battery_optimization_action),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { mainViewModel.setBatteryOptimizationPromptShown(true) }) {
                    HarkText(
                        text = stringResource(R.string.battery_optimization_dismiss),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = 120.dp + navigationBarsPadding) // Lift above navbar
                ) { data ->
                    HarkToast(
                        snackbarData = data,
                        icon = Icons.Default.Info
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Navigation.ROUTE_HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                composable(Navigation.ROUTE_HOME) {
                    HomeScreen(
                        isStreaming = uiState.isStreaming,
                        onStreamButtonClick = onToggleStreaming,
                        hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                        isModelAvailable = uiState.isModelAvailable
                    )
                }
                composable(Navigation.ROUTE_TRANSCRIBE) {
                    TranscribeScreen(
                        isStreaming = uiState.isStreaming,
                        transcription = uiState.transcription,
                        activeSoundEvents = uiState.activeSoundEvents,
                        onClearTranscription = onClearTranscription,
                        onShareTranscription = onShareTranscription,
                        fontSize = settingsState.transcriptionFontSize
                    )
                }
                composable(Navigation.ROUTE_HISTORY) {
                    HistoryScreen(
                        history = uiState.history,
                        onDeleteAll = { mainViewModel.deleteAllHistory() },
                        onDeleteById = { id -> mainViewModel.deleteHistoryItem(id) },
                        onShare = onShareTranscription
                    )
                }
                composable(Navigation.ROUTE_SETTINGS) {
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption
                    )
                }
            }
        }

        BottomNavBar(
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
            isModelAvailable = uiState.isModelAvailable,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
