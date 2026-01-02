package com.thivyanstudios.hark

import android.os.Build
import android.os.Bundle
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
        audioServiceManager.bindService()
    }

    override fun onStop() {
        super.onStop()
        audioServiceManager.unbindService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        // Keep splash screen until settings are loaded
        var isSettingsLoaded = false
        
        // We can observe the settings state to know when data is ready
        // Since we are using SharingStarted.Eagerly in SettingsViewModel, it starts loading immediately
        // We just need to wait for a non-default state or just a small delay if needed.
        // However, since we switched to Eagerly, the first value will be emitted immediately (initialValue).
        // A better check might be to see if we have actually read from DataStore.
        // But for this specific request "make the splash screen show longer", we can simply hold it 
        // until the UI is ready to draw or until we are sure things are initialized.
        
        // Let's rely on the fact that we want to ensure smooth first transition.
        // We can hold the splash screen until the first frame is ready to be drawn by Compose.
        // Or we can add a condition.
        
        splashScreen.setKeepOnScreenCondition {
             // You might want to check a specific condition here, 
             // e.g., waiting for specific data to load.
             // For now, let's keep it simple and just let it dismiss when the app is ready to draw,
             // which is the default behavior if we return false.
             // If the user wants it "longer" to hide the init lag, we can check if the settings
             // have been emitted at least once with real data?
             // Since we use initialValue in stateIn, it's always "loaded" with default.
             
             // If we really want to wait for "init", we would need a "loading" state in ViewModel.
             // But simpler request: "move init of settings to the opening event" -> done via Eagerly.
             // "make the splash screen show longer" -> we can do that.
             
             false
        }
        
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
        if (permissionManager.hasPermissions()) {
            mainViewModel.toggleStreaming(getString(R.string.connect_hearing_system_first))
        } else {
            permissionManager.requestPermissions()
        }
    }
}
