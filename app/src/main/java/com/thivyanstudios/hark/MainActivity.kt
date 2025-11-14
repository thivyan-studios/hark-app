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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var audioService: AudioStreamingService? = null
    private var bound = false
    private var isStreaming by mutableStateOf(false)

    private val streamingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.thivyanstudios.hark.STREAMING_STATE_CHANGED") {
                isStreaming = intent.getBooleanExtra("isStreaming", false)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioStreamingService.LocalBinder
            audioService = binder.getService()
            bound = true
            isStreaming = audioService?.isStreaming() == true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            bound = false
            isStreaming = false
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        Intent(this, AudioStreamingService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
        val streamingFilter = IntentFilter("com.thivyanstudios.hark.STREAMING_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(streamingStateReceiver, streamingFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(streamingStateReceiver, streamingFilter)
        }
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
        unregisterReceiver(streamingStateReceiver)
    }

    override fun onResume() {
        super.onResume()
        isStreaming = audioService?.isStreaming() == true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val version = try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                "Developed by Thivyan Pillay (Stable-Release v${packageInfo.versionName})"
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                "Developed by Thivyan Pillay (Version not found)"
            }

            MainScreen(
                isStreaming = isStreaming,
                versionName = version,
                onStreamButtonClick = { toggleStreaming() }
            )
        }

        startService(Intent(this, AudioStreamingService::class.java))
        requestPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun toggleStreaming() {
        if (!hasPermissions()) {
            requestPermissions()
            return
        }

        if (audioService?.isStreaming() == true) {
            audioService?.stopStreaming()
            isStreaming = false
        } else {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val hasHearingAid = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_HEARING_AID }
            if (hasHearingAid) {
                audioService?.startStreaming()
                isStreaming = true
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