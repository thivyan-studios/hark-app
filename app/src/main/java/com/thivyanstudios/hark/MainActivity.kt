package com.thivyanstudios.hark

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
             mainViewModel.uiState.value.isLoading
        }
        
        enableEdgeToEdge()

        permissionManager = PermissionManager(
            this,
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                HarkLog.i("MainActivity", "Permission result: $permissions")
                permissionManager.handlePermissionsResult(permissions)
                mainViewModel.setPermissionsHandled(true)
            }
        ) { mainViewModel.showPermissionsRequiredMessage(getString(R.string.permissions_required)) }

        if (permissionManager.hasPermissions()) {
            mainViewModel.setPermissionsHandled(true)
        } else {
            permissionManager.requestPermissions()
        }

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
                    mainViewModel = mainViewModel,
                    settingsViewModel = settingsViewModel,
                    onToggleStreaming = { toggleStreaming() },
                    onClearTranscription = { mainViewModel.clearTranscription() },
                    onShareTranscription = { text -> shareTranscription(text) },
                    onRequestBatteryOptimizationExemption = { requestBatteryOptimizationExemption() }
                )
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to the general settings list if the direct prompt fails
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun shareTranscription(text: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_transcription_title))
        startActivity(shareIntent)
    }

    private fun toggleStreaming() {
        HarkLog.i("MainActivity", "Toggle streaming button clicked")
        if (permissionManager.hasPermissions()) {
            audioServiceManager.startService() // Ensure service is started
            @SuppressLint("MissingPermission")
            mainViewModel.toggleStreaming(getString(R.string.connect_hearing_system_first))
        } else {
            permissionManager.requestPermissions()
        }
    }
}
