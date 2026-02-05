package com.thivyanstudios.hark

import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thivyanstudios.hark.service.AudioServiceManager
import com.thivyanstudios.hark.ui.HarkAppContent
import com.thivyanstudios.hark.ui.theme.HarkTheme
import com.thivyanstudios.hark.ui.viewmodel.MainViewModel
import com.thivyanstudios.hark.ui.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.util.HarkLog
import com.thivyanstudios.hark.util.PermissionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var audioServiceManager: AudioServiceManager
    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private lateinit var permissionManager: PermissionManager

    override fun onStart() {
        super.onStart()
        HarkLog.i("MainActivity", "onStart")
        audioServiceManager.bindService()
    }

    override fun onStop() {
        super.onStop()
        HarkLog.i("MainActivity", "onStop")
        audioServiceManager.unbindService()
    }

    override fun onDestroy() {
        super.onDestroy()
        HarkLog.i("MainActivity", "onDestroy")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        HarkLog.i("MainActivity", "onCreate")
        
        // Keep splash screen until the app is ready to draw
        splashScreen.setKeepOnScreenCondition {
             false
        }
        
        enableEdgeToEdge()

        permissionManager = PermissionManager(
            this,
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                HarkLog.i("MainActivity", "Permission result: $permissions")
                permissionManager.handlePermissionsResult(permissions)
            }
        ) { mainViewModel.showPermissionsRequiredMessage(getString(R.string.permissions_required)) }

        permissionManager.requestPermissions()

        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(true) {
                mainViewModel.snackbarEvents.collect { message ->
                    // Only show snackbar if there isn't one already showing
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
                }
            }

            // Handle Screen On/Off Flag
            LaunchedEffect(uiState.keepScreenOn) {
                if (uiState.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            HarkTheme {
                HarkAppContent(
                    uiState = uiState,
                    snackbarHostState = snackbarHostState,
                    settingsViewModel = settingsViewModel,
                    onToggleStreaming = { toggleStreaming() }
                )
            }
        }

        audioServiceManager.startService()
    }

    private fun toggleStreaming() {
        HarkLog.i("MainActivity", "Toggle streaming button clicked")
        if (permissionManager.hasPermissions()) {
            mainViewModel.toggleStreaming(getString(R.string.connect_hearing_system_first))
        } else {
            permissionManager.requestPermissions()
        }
    }
}
