package com.thivyanstudios.hark.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thivyanstudios.hark.ui.screens.BottomNavBar
import com.thivyanstudios.hark.ui.screens.HomeScreen
import com.thivyanstudios.hark.ui.screens.SettingsScreen
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.util.Constants.Navigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HarkAppContent(
    uiState: MainUiState,
    snackbarHostState: SnackbarHostState,
    settingsViewModel: SettingsViewModel,
    onToggleStreaming: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Navigation.ROUTE_HOME

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
            )
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
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
                    isTestMode = uiState.isTestMode,
                    onStreamButtonClick = onToggleStreaming,
                    hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
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
