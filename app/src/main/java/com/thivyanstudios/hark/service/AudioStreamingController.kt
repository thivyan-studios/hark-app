package com.thivyanstudios.hark.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface defining the capabilities of the Audio Streaming Service exposed to clients.
 * This improves encapsulation by hiding the Android Service lifecycle methods and implementation details.
 */
interface AudioStreamingController {
    val isStreaming: StateFlow<Boolean>
    val hearingAidConnected: StateFlow<Boolean>

    fun startStreaming()
    fun stopStreaming()
}
