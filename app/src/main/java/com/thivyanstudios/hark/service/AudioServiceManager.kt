package com.thivyanstudios.hark.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.thivyanstudios.hark.util.HarkLog
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

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            HarkLog.i("AudioServiceManager", "Service connected: $name")
            val localBinder = binder as AudioStreamingService.LocalBinder
            _service.value = localBinder.getService()
            isBound.set(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            HarkLog.i("AudioServiceManager", "Service disconnected: $name")
            _service.value = null
            isBound.set(false)
        }

        override fun onBindingDied(name: ComponentName?) {
            HarkLog.w("AudioServiceManager", "Binding died: $name")
             _service.value = null
            isBound.set(false)
        }

        override fun onNullBinding(name: ComponentName?) {
            HarkLog.w("AudioServiceManager", "Null binding: $name")
             _service.value = null
            isBound.set(false)
        }
    }

    fun startService() {
        HarkLog.i("AudioServiceManager", "Starting service")
        val intent = Intent(context, AudioStreamingService::class.java)
        context.startService(intent)
    }

    fun stopService() {
        HarkLog.i("AudioServiceManager", "Stopping service")
        val intent = Intent(context, AudioStreamingService::class.java)
        context.stopService(intent)
    }

    fun bindService() {
        if (!isBound.get()) {
            HarkLog.i("AudioServiceManager", "Binding service")
            val intent = Intent(context, AudioStreamingService::class.java)
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                HarkLog.e("AudioServiceManager", "Failed to bind service")
                isBound.set(false)
            }
        }
    }

    fun unbindService() {
        if (isBound.get()) {
            HarkLog.i("AudioServiceManager", "Unbinding service")
            try {
                context.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                HarkLog.e("AudioServiceManager", "Service not registered or already unbound", e)
            }
            isBound.set(false)
            _service.value = null
        }
    }
}
