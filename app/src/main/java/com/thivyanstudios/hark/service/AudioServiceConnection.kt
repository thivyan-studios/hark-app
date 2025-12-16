package com.thivyanstudios.hark.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AudioServiceConnection : ServiceConnection {
    var audioService: AudioStreamingService? by mutableStateOf(null)
    var bound by mutableStateOf(false)

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
