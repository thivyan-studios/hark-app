package com.thivyanstudios.hark

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thivyanstudios.hark.ui.screens.BottomNavBar
import com.thivyanstudios.hark.ui.screens.HomeScreen
import com.thivyanstudios.hark.ui.screens.SettingsScreen
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.ui.theme.HarkTheme
import com.thivyanstudios.hark.util.PermissionManager
import com.thivyanstudios.hark.ui.viewmodel.MainViewModel
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var audioServiceManager: AudioServiceManager
    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var permissionManager: PermissionManager

    override fun onStart() {
        super.onStart()
        audioServiceManager.bindService()
    }

    override fun onStop() {
        super.onStop()
        audioServiceManager.unbindService()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionManager = PermissionManager(
            this,
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissionManager.handlePermissionsResult(permissions)
            }
        ) { mainViewModel.showPermissionsRequiredMessage(getString(R.string.permissions_required)) }

        permissionManager.requestPermissions()

        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(true) {
                mainViewModel.snackbarEvents.collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            if (uiState.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            HarkTheme(darkTheme = uiState.isDarkMode) {
                val pagerState = rememberPagerState(pageCount = { 2 })
                val coroutineScope = rememberCoroutineScope()
                val currentRoute = when (pagerState.currentPage) {
                    0 -> "home"
                    else -> "settings"
                }

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
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(if (route == "settings") 1 else 0)
                                }
                            },
                            hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                        )
                    },
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                ) { innerPadding ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        page ->
                        when(page) {
                            0 -> HomeScreen(
                                isStreaming = uiState.isStreaming,
                                onStreamButtonClick = { toggleStreaming() },
                                hapticFeedbackEnabled = uiState.hapticFeedbackEnabled
                            )
                            1 -> SettingsScreen(
                                settingsViewModel = settingsViewModel
                            )
                        }

                    }

                }
            }
        }

        audioServiceManager.startService()
    }

    private fun toggleStreaming() {
        if (permissionManager.hasPermissions()) {
            mainViewModel.toggleStreaming(getString(R.string.connect_hearing_system_first))
        } else {
            permissionManager.requestPermissions()
        }
    }
}
