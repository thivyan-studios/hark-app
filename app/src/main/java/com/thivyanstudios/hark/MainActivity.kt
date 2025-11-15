package com.thivyanstudios.hark

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.screens.BottomNavBar
import com.thivyanstudios.hark.screens.HomeScreen
import com.thivyanstudios.hark.screens.SettingsScreen
import com.thivyanstudios.hark.service.AudioStreamingService
import com.thivyanstudios.hark.ui.theme.HarkTheme
import com.thivyanstudios.hark.viewmodel.SettingsViewModel
import com.thivyanstudios.hark.viewmodel.SettingsViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private var audioService: AudioStreamingService? by mutableStateOf(null)
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioStreamingService.LocalBinder
            audioService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            bound = false
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AudioStreamingService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userPreferencesRepository = UserPreferencesRepository(applicationContext)
        val settingsViewModelFactory = SettingsViewModelFactory(userPreferencesRepository)
        val settingsViewModel: SettingsViewModel by viewModels { settingsViewModelFactory }

        setContent {
            val service = audioService
            val isStreaming by service?.isStreaming?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
            val hearingAidConnected by service?.hearingAidConnected?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

            val hapticFeedbackEnabled by settingsViewModel.hapticFeedbackEnabled.collectAsStateWithLifecycle()
            val isDarkMode by settingsViewModel.isDarkMode.collectAsStateWithLifecycle()
            val keepScreenOn by settingsViewModel.keepScreenOn.collectAsStateWithLifecycle()

            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            HarkTheme(darkTheme = isDarkMode) {
                val version = try {
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    "Developed by Thivyan Pillay (Stable-Release v${packageInfo.versionName})"
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                    "Developed by Thivyan Pillay (Version not found)"
                }

                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { BottomNavBar(navController = navController, hapticFeedbackEnabled = hapticFeedbackEnabled) }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                isStreaming = isStreaming,
                                onStreamButtonClick = @androidx.annotation.RequiresPermission(
                                    android.Manifest.permission.RECORD_AUDIO
                                ) { toggleStreaming() },
                                hapticFeedbackEnabled = hapticFeedbackEnabled
                            )
                        }
                        composable("settings") {
                            SettingsScreen(versionName = version, factory = settingsViewModelFactory)
                        }
                    }
                }
            }
        }

        startService(Intent(this, AudioStreamingService::class.java))
        requestPermissions()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @RequiresApi(Build.VERSION_CODES.S)
    private fun toggleStreaming() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }

        if (audioService?.isStreaming?.value == true) {
            audioService?.stopStreaming()
        } else {
            if (audioService?.hearingAidConnected?.value == true) {
                audioService?.startStreaming()
            } else {
                Toast.makeText(this, "Connect your hearing system first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasPermissions(): Boolean {
        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
        }
    }
}
