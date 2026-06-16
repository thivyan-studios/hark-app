package com.thivyanstudios.hark.service

import kotlinx.coroutines.flow.StateFlow

interface AudioStreamingController {
    val isStreaming: StateFlow<Boolean>
    val hearingAidConnected: StateFlow<Boolean>
    val audioLevel: StateFlow<Float>
    val inputLevel: StateFlow<Float>
    val outputLevel: StateFlow<Float>

    fun startStreaming()
    fun stopStreaming()
}
