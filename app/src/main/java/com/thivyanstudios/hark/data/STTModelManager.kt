package com.thivyanstudios.hark.data

import android.content.Context
import com.thivyanstudios.hark.data.model.SherpaModel
import com.thivyanstudios.hark.di.IoDispatcher
import com.thivyanstudios.hark.util.HarkLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class STTModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val okHttpClient = OkHttpClient()
    
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _modelStoreUpdateTrigger = MutableStateFlow(System.currentTimeMillis())
    val modelStoreUpdateTrigger: StateFlow<Long> = _modelStoreUpdateTrigger.asStateFlow()

    fun notifyModelStoreChanged() {
        _modelStoreUpdateTrigger.value = System.currentTimeMillis()
    }

    val availableModels = listOf(
        SherpaModel(
            id = "zipformer-en-streaming",
            name = "Streaming Zipformer (English)",
            description = "High-speed, real-time English transcription. Best for hearing aid use.",
            encoderUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/encoder-epoch-99-avg-1.onnx",
            decoderUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/decoder-epoch-99-avg-1.onnx",
            joinerUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/joiner-epoch-99-avg-1.onnx",
            tokensUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/tokens.txt",
            encoderSize = 100_000_000L,
            decoderSize = 10_000_000L,
            joinerSize = 20_000_000L,
            tokensSize = 500_000L
        )
    )

    fun isModelDownloaded(model: SherpaModel): Boolean {
        val encoder = File(context.filesDir, model.encoderFileName)
        val decoder = File(context.filesDir, model.decoderFileName)
        val joiner = File(context.filesDir, model.joinerFileName)
        val tokens = File(context.filesDir, model.tokensFileName)
        
        return encoder.exists() && decoder.exists() && joiner.exists() && tokens.exists()
    }

    suspend fun downloadModel(model: SherpaModel): Boolean = withContext(ioDispatcher) {
        if (isModelDownloaded(model)) return@withContext true

        val files = listOf(
            model.encoderUrl to model.encoderFileName,
            model.decoderUrl to model.decoderFileName,
            model.joinerUrl to model.joinerFileName,
            model.tokensUrl to model.tokensFileName
        )

        var success = true
        for (i in files.indices) {
            val (url, fileName) = files[i]
            val result = downloadFile(url, fileName) { progress ->
                // Overall progress across 4 files
                val overallProgress = (i.toFloat() + progress) / files.size
                _downloadProgress.value = _downloadProgress.value + (model.id to overallProgress)
            }
            if (!result) {
                success = false
                break
            }
        }

        _downloadProgress.value = _downloadProgress.value - model.id
        if (success) {
            HarkLog.i("STTModelManager", "Downloaded model: ${model.name}")
            notifyModelStoreChanged()
        }
        success
    }

    private suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): Boolean = withContext(ioDispatcher) {
        val file = File(context.filesDir, fileName)
        val request = Request.Builder().url(url).build()
        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false
            
            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            onProgress(totalRead.toFloat() / totalBytes)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            HarkLog.e("STTModelManager", "Failed to download file: $fileName", e)
            file.delete()
            false
        }
    }

    fun deleteModel(modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        
        val files = listOf(
            File(context.filesDir, model.encoderFileName),
            File(context.filesDir, model.decoderFileName),
            File(context.filesDir, model.joinerFileName),
            File(context.filesDir, model.tokensFileName)
        )

        var anyDeleted = false
        files.forEach { if (it.exists()) { it.delete(); anyDeleted = true } }
        
        if (anyDeleted) notifyModelStoreChanged()
        return anyDeleted
    }
}
