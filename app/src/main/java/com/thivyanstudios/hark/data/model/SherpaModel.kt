package com.thivyanstudios.hark.data.model

data class SherpaModel(
    val id: String,
    val name: String,
    val description: String,
    val encoderUrl: String,
    val decoderUrl: String,
    val joinerUrl: String,
    val tokensUrl: String,
    val encoderSize: Long,
    val decoderSize: Long,
    val joinerSize: Long,
    val tokensSize: Long,
    val isAsset: Boolean = false
) {
    val totalSizeBytes: Long = encoderSize + decoderSize + joinerSize + tokensSize
    
    val encoderFileName: String = "$id-encoder.onnx"
    val decoderFileName: String = "$id-decoder.onnx"
    val joinerFileName: String = "$id-joiner.onnx"
    val tokensFileName: String = "$id-tokens.txt"
}
