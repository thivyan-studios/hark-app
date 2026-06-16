package com.thivyanstudios.hark.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thivyanstudios.hark.ui.screens.BottomNavBar
import com.thivyanstudios.hark.ui.screens.EqualizerScreen
import com.thivyanstudios.hark.ui.screens.HearingTestScreen
import com.thivyanstudios.hark.ui.screens.HomeScreen
import com.thivyanstudios.hark.ui.screens.SettingsScreen
import com.thivyanstudios.hark.ui.theme.HarkText
import com.thivyanstudios.hark.ui.theme.HarkToast
import com.thivyanstudios.hark.ui.viewmodel.EqualizerViewModel
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
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Navigation.ROUTE_HOME

    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Bluetooth-only warning banner
            AnimatedVisibility(
                visible = uiState.isBluetoothOnly && !uiState.isWiredHeadphoneConnected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF3E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Please use wired earphones for best results",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "Bluetooth latency too high / \u84DD\u7259\u5EF6\u8FDF\u8FC7\u9AD8",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBF360C)
                            )
                        }
                    }
                }
            }

            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = 120.dp + navigationBarsPadding)
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
                            inputLevel = uiState.inputLevel,
                            outputLevel = uiState.outputLevel,
                            isConnected = uiState.isServiceConnected,
                            latencyMs = uiState.latencyMs,
                            inputDeviceName = uiState.inputDeviceName,
                            outputDeviceName = uiState.outputDeviceName
                        )
                    }
                    composable(Navigation.ROUTE_EQUALIZER) {
                        val eqViewModel = hiltViewModel<EqualizerViewModel>()
                        EqualizerScreen(viewModel = eqViewModel)
                    }
                    composable(Navigation.ROUTE_HEARING_TEST) {
                        val eqViewModel = hiltViewModel<EqualizerViewModel>()
                        HearingTestScreen(
                            onApplyToEq = { gains ->
                                eqViewModel.applyHearingTestResults(gains)
                                navController.navigate(Navigation.ROUTE_EQUALIZER) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable(Navigation.ROUTE_SETTINGS) {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel
                        )
                    }
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
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
