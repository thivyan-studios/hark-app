package com.thivyanstudios.hark.ui.model

data class AudioDeviceOption(
    val id: Int,
    val name: String,
    val type: Int,
    val isRecommended: Boolean = true
)
