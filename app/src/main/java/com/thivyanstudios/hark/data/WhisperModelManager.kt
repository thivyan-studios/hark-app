package com.thivyanstudios.hark.data

import android.content.Context
import com.thivyanstudios.hark.data.model.WhisperModel
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
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
        WhisperModel(
            id = "ggml-base-q8_0.bin",
            name = "Base (Standard)",
            description = "Good balance of speed and accuracy. Best for most devices.",
            url = "", // Included in assets
            sizeBytes = 77_000_000L,
            isAsset = true
        ),
        WhisperModel(
            id = "ggml-tiny.en-q8_0",
            name = "Tiny (Fastest)",
            description = "Lowest battery usage, but less accurate.",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q8_0.bin",
            sizeBytes = 31_000_000L
        ),
        WhisperModel(
            id = "ggml-base.en-q8_0",
            name = "Base English (Improved)",
            description = "Optimized for English speech.",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q8_0.bin",
            sizeBytes = 77_000_000L
        ),
        WhisperModel(
            id = "ggml-large-v3-turbo-q5_0",
            name = "Turbo (Flagship Only)",
            description = "Maximum accuracy. Requires a modern, powerful device.",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
            sizeBytes = 550_000_000L
        )
    )

    fun isModelDownloaded(model: WhisperModel): Boolean {
        if (model.isAsset) return true
        val file = File(context.filesDir, model.fileName)
        return file.exists() && file.length() >= model.sizeBytes * 0.9 // Simple check
    }

    suspend fun downloadModel(model: WhisperModel): Boolean = withContext(ioDispatcher) {
        if (model.isAsset) return@withContext true
        
        val file = File(context.filesDir, model.fileName)
        if (isModelDownloaded(model)) return@withContext true

        val request = Request.Builder().url(model.url).build()
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
                            val progress = totalRead.toFloat() / totalBytes
                            _downloadProgress.value = _downloadProgress.value + (model.id to progress)
                        }
                    }
                }
            }
            _downloadProgress.value = _downloadProgress.value - model.id
            HarkLog.i("WhisperModelManager", "Downloaded model: ${model.name}")
            notifyModelStoreChanged()
            true
        } catch (e: Exception) {
            HarkLog.e("WhisperModelManager", "Failed to download model: ${model.name}", e)
            file.delete()
            _downloadProgress.value = _downloadProgress.value - model.id
            false
        }
    }

    fun getModelFile(modelId: String): File? {
        val model = availableModels.find { it.id == modelId } ?: return null
        return File(context.filesDir, model.fileName)
    }

    fun deleteModel(modelId: String): Boolean {
        val model = availableModels.find { it.id == modelId } ?: return false
        if (model.isAsset) return false
        
        val file = File(context.filesDir, model.fileName)
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) notifyModelStoreChanged()
            deleted
        } else false
    }
}
