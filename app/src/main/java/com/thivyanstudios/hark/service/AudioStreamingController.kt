package com.thivyanstudios.hark.service

import com.thivyanstudios.hark.ui.model.AudioDeviceOption
import kotlinx.coroutines.flow.StateFlow

interface AudioStreamingController {
    val isStreaming: StateFlow<Boolean>
    val hearingAidConnected: StateFlow<Boolean>
    val audioLevel: StateFlow<Float>
    val inputLevel: StateFlow<Float>
    val outputLevel: StateFlow<Float>
    val latencyMs: StateFlow<Float>
    val outputDeviceName: StateFlow<String>
    val inputDeviceName: StateFlow<String>

    val availableInputDevices: StateFlow<List<AudioDeviceOption>>
    val availableOutputDevices: StateFlow<List<AudioDeviceOption>>
    val selectedInputDeviceId: StateFlow<Int>
    val selectedOutputDeviceId: StateFlow<Int>

    fun startStreaming()
    fun stopStreaming()
    fun selectInputDevice(id: Int)
    fun selectOutputDevice(id: Int)
}
