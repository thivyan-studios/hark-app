package com.thivyanstudios.hark.service

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Keeps track of whether something HARK can stream to is connected: hearing aids, or (when
 * Bluetooth Headset Support is on) Bluetooth, wired and USB headsets. Tells the service when
 * devices come and go, so it can restart or stop the stream.
 */
class HearingDeviceMonitor @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    interface Listener {
        /** Something was plugged in or connected. */
        fun onDevicesAdded()

        /** Something was removed. [streamTargetRemoved] is true if it was a device HARK streams to. */
        fun onDevicesRemoved(streamTargetRemoved: Boolean)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _connected = MutableStateFlow(false)
    /** True while a device HARK can stream to is connected. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Bluetooth Headset Support setting: when off, only hearing aids count. */
    var headsetsAllowed = false
        private set

    private var listener: Listener? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            HarkLog.i(TAG, "Audio devices added")
            refresh()
            listener?.onDevicesAdded()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            HarkLog.i(TAG, "Audio devices removed")
            refresh()
            listener?.onDevicesRemoved(streamTargetRemoved = removedDevices?.any { isStreamTarget(it.type) } == true)
        }
    }

    fun start(listener: Listener) {
        this.listener = listener
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
        listener = null
    }

    fun setHeadsetsAllowed(allowed: Boolean) {
        headsetsAllowed = allowed
        refresh()
    }

    /** Re-checks the connected outputs. */
    fun refresh() {
        val isConnected = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isStreamTarget(it.type) }
        HarkLog.i(TAG, "Hearing aid connected: $isConnected")
        _connected.value = isConnected
    }

    private fun isStreamTarget(type: Int): Boolean {
        if (!headsetsAllowed) return type == AudioDeviceInfo.TYPE_HEARING_AID
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        }
    }

    private companion object {
        const val TAG = "HearingDeviceMonitor"
    }
}
