package com.thivyanstudios.hark.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeadphoneDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isWiredConnected = MutableStateFlow(false)
    val isWiredConnected: StateFlow<Boolean> = _isWiredConnected.asStateFlow()

    private val _isBluetoothOnly = MutableStateFlow(false)
    val isBluetoothOnly: StateFlow<Boolean> = _isBluetoothOnly.asStateFlow()

    private var isMonitoring = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            updateStatus()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            updateStatus()
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        updateStatus()
        HarkLog.i(TAG, "Headphone monitoring started")
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
        HarkLog.i(TAG, "Headphone monitoring stopped")
    }

    private fun updateStatus() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val hasWired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        val hasBluetooth = outputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }

        _isWiredConnected.value = hasWired
        _isBluetoothOnly.value = hasBluetooth && !hasWired

        HarkLog.i(TAG, "Wired=$hasWired, BT-only=${hasBluetooth && !hasWired}")
    }

    companion object {
        private const val TAG = "HeadphoneDetector"
    }
}
