package com.thivyanstudios.hark.ui

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
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
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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

    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Shared between the screens (blur source) and the floating navbar (blur effect)
    val hazeState = rememberHazeState()

    // Tablets (shortest side >= 600dp, in either orientation) get Home and the other
    // pages side by side. Phones keep the single-screen layout.
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val startRoute = if (isTablet) Navigation.ROUTE_SETTINGS else Navigation.ROUTE_HOME
    val currentRoute = navBackStackEntry?.destination?.route ?: startRoute

    val homeScreen: @Composable () -> Unit = {
        HomeScreen(
            isStreaming = uiState.isStreaming,
            onStreamButtonClick = onToggleStreaming,
            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
            isModelAvailable = uiState.isModelAvailable
        )
    }

    val onNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The pages reachable from the navbar. On tablets Home lives in its own pane instead.
    val pages: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = modifier.hazeSource(hazeState)
        ) {
            if (!isTablet) {
                composable(Navigation.ROUTE_HOME) { homeScreen() }
            }
            composable(Navigation.ROUTE_TRANSCRIBE) {
                TranscribeScreen(
                    isStreaming = uiState.isStreaming,
                    transcription = uiState.transcription,
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
        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                    homeScreen()
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Box(modifier = Modifier.weight(0.58f).fillMaxHeight()) {
                    pages(Modifier.fillMaxSize())
                    // With no transcription model, Settings is the only page, so no navbar is needed
                    if (uiState.isModelAvailable) {
                        BottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = onNavigate,
                            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                            isModelAvailable = uiState.isModelAvailable,
                            hazeState = hazeState,
                            showStreamTab = false,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                pages(Modifier.fillMaxSize())
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                    isModelAvailable = uiState.isModelAvailable,
                    hazeState = hazeState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
