package com.thivyanstudios.hark.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _service = MutableStateFlow<AudioStreamingService?>(null)
    val service = _service.asStateFlow()

    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as AudioStreamingService.LocalBinder
            _service.value = localBinder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            bound = false
        }
    }

    fun startService() {
        val intent = Intent(context, AudioStreamingService::class.java)
        context.startService(intent)
    }

    fun bindService() {
        val intent = Intent(context, AudioStreamingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (bound) {
            context.unbindService(connection)
            bound = false
            _service.value = null
        }
    }
}
