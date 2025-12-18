package com.thivyanstudios.hark

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thivyanstudios.hark.screens.BottomNavBar
import com.thivyanstudios.hark.screens.HomeScreen
import com.thivyanstudios.hark.screens.SettingsScreen
import com.thivyanstudios.hark.service.AudioStreamingService
import com.thivyanstudios.hark.ui.MainUiState
import com.thivyanstudios.hark.ui.theme.HarkTheme
import com.thivyanstudios.hark.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private val snackbarChannel = Channel<String>()
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.any { !it.value }) {
            lifecycleScope.launch {
                snackbarChannel.send(getString(R.string.permissions_required))
            }
        }
    }
    private var audioService by mutableStateOf<AudioStreamingService?>(null)
    private var bound by mutableStateOf(false)

    private val audioServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioStreamingService.LocalBinder
            audioService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioStreamingService::class.java).also { intent ->
            bindService(intent, audioServiceConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(audioServiceConnection)
            bound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions()

        setContent {
            val service = audioService
            val uiState by remember(service, bound) {
                if (service != null && bound) {
                    combine(
                        service.isStreaming,
                        service.hearingAidConnected,
                        settingsViewModel.hapticFeedbackEnabled,
                        settingsViewModel.isDarkMode,
                        settingsViewModel.keepScreenOn
                    ) { isStreaming, hearingAidConnected, hapticFeedbackEnabled, isDarkMode, keepScreenOn ->
                        MainUiState(
                            isStreaming = isStreaming,
                            hearingAidConnected = hearingAidConnected,
                            hapticFeedbackEnabled = hapticFeedbackEnabled,
                            isDarkMode = isDarkMode,
                            keepScreenOn = keepScreenOn
                        )
                    }
                } else {
                    combine(
                        settingsViewModel.hapticFeedbackEnabled,
                        settingsViewModel.isDarkMode,
                        settingsViewModel.keepScreenOn
                    ) { hapticFeedbackEnabled, isDarkMode, keepScreenOn ->
                        MainUiState(
                            hapticFeedbackEnabled = hapticFeedbackEnabled,
                            isDarkMode = isDarkMode,
                            keepScreenOn = keepScreenOn
                        )
                    }
                }
            }.collectAsStateWithLifecycle(initialValue = MainUiState())

            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(snackbarChannel) {
                snackbarChannel.receiveAsFlow().collect { message ->
                    snackbarHostState.showSnackbar(message)
                }
            }

            if (uiState.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            HarkTheme(darkTheme = uiState.isDarkMode) {
                val navController = rememberNavController()
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
                    bottomBar = { BottomNavBar(navController = navController, hapticFeedbackEnabled = uiState.hapticFeedbackEnabled) },
                    contentWindowInsets = ScaffoldDefaults.contentWindowInsets
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                    ) {
                        composable("home") {
                            HomeScreen(
                                isStreaming = uiState.isStreaming,
                                onStreamButtonClick = { toggleStreaming() },
                                hapticFeedbackEnabled = uiState.hapticFeedbackEnabled,
                                innerPadding = innerPadding
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                settingsViewModel = settingsViewModel,
                                innerPadding = innerPadding
                            )
                        }
                    }
                }
            }
        }

        startService(Intent(this, AudioStreamingService::class.java))
    }

    private fun toggleStreaming() {
        if (hasPermissions()) {
            val service = audioService
            if (service?.isStreaming?.value == true) {
                service.stopStreaming()
            } else {
                if (service?.hearingAidConnected?.value == true) {
                    service.startStreaming()
                } else {
                    lifecycleScope.launch {
                        snackbarChannel.send(getString(R.string.connect_hearing_system_first))
                    }
                }
            }
        } else {
            requestPermissions()
        }
    }

    private fun hasPermissions(): Boolean {
        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
