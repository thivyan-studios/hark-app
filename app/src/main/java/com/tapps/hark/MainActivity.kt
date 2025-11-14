package com.tapps.hark

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private var audioService: AudioStreamingService? = null
    private var bound = false
    private lateinit var micIcon: ImageView

    private val streamingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.tapps.hark.STREAMING_STATE_CHANGED") {
                updateMicIcon(intent.getBooleanExtra("isStreaming", false))
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioStreamingService.LocalBinder
            audioService = binder.getService()
            bound = true
            updateMicIcon(audioService?.isStreaming() == true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            bound = false
            updateMicIcon(false)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        Intent(this, AudioStreamingService::class.java).also { intent ->
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
        val streamingFilter = IntentFilter("com.tapps.hark.STREAMING_STATE_CHANGED")
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
        updateMicIcon(audioService?.isStreaming() == true)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        micIcon = findViewById(R.id.imgStream)

        startService(Intent(this, AudioStreamingService::class.java))
        requestPermissions()

        micIcon.setOnClickListener {
            if (hasPermissions()) {
                toggleStreaming()
            } else {
                requestPermissions()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun toggleStreaming() {
        if (audioService?.isStreaming() == true) {
            audioService?.stopStreaming()
            updateMicIcon(false)
        } else {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val hasHearingAid = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_HEARING_AID }
            if (hasHearingAid) {
                audioService?.startStreaming()
                updateMicIcon(true)
            } else {
                Toast.makeText(this, "Connect your hearing system first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMicIcon(isStreaming: Boolean) {
        runOnUiThread {
            micIcon.setImageResource(if (isStreaming) R.drawable.white_mic_on else R.drawable.white_mic_off)
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