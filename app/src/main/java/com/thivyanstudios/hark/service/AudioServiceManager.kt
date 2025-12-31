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
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class AudioServiceManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val _service = MutableStateFlow<AudioStreamingController?>(null)
    val service = _service.asStateFlow()

    private val isBound = AtomicBoolean(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as AudioStreamingService.LocalBinder
            _service.value = localBinder.getService()
            isBound.set(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            isBound.set(false)
        }

        override fun onBindingDied(name: ComponentName?) {
             _service.value = null
            isBound.set(false)
        }

        override fun onNullBinding(name: ComponentName?) {
             _service.value = null
            isBound.set(false)
        }
    }

    fun startService() {
        val intent = Intent(context, AudioStreamingService::class.java)
        context.startService(intent)
    }

    fun stopService() {
        val intent = Intent(context, AudioStreamingService::class.java)
        context.stopService(intent)
    }

    fun bindService() {
        if (!isBound.get()) {
            val intent = Intent(context, AudioStreamingService::class.java)
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                isBound.set(false)
            }
        }
    }

    fun unbindService() {
        if (isBound.get()) {
            try {
                context.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                // Service not registered or already unbound
            }
            isBound.set(false)
            _service.value = null
        }
    }
}
