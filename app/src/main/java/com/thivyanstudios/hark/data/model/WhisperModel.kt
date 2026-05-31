package com.thivyanstudios.hark.data.model

data class WhisperModel(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
    val isAsset: Boolean = false
) {
    val fileName: String get() = if (isAsset) id else "$id.bin"
}
