package com.thivyanstudios.hark.data

import com.thivyanstudios.hark.R
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
            nameRes = R.string.model_zipformer_en_name,
            descriptionRes = R.string.model_zipformer_en_description,
            // int8-quantised encoder & joiner (same set Sherpa-ONNX's own Android demo uses):
            // ~129 MB instead of ~296 MB and roughly twice as fast on phone CPUs, with
            // near-identical accuracy. The decoder is tiny, so it stays full precision.
            encoderUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/encoder-epoch-99-avg-1.int8.onnx",
            decoderUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/decoder-epoch-99-avg-1.onnx",
            joinerUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/joiner-epoch-99-avg-1.int8.onnx",
            tokensUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-02-21/resolve/main/tokens.txt",
            // Exact sizes of the files at the URLs above
            encoderSize = 126_967_571L,
            decoderSize = 2_093_080L,
            joinerSize = 259_380L,
            tokensSize = 5_048L
        )
    )

    init {
        removeStaleModelFiles()
    }

    /**
     * Deletes model files whose size doesn't match what the current model expects, e.g.
     * the ~293 MB full-precision encoder left over from before the switch to int8, or a
     * truncated file from an old interrupted download. Frees the space and lets the
     * Settings screen offer a clean download.
     */
    private fun removeStaleModelFiles() {
        availableModels.forEach { model ->
            model.files().forEach { (file, expectedSize) ->
                if (file.exists() && file.length() != expectedSize) {
                    HarkLog.i("STTModelManager", "Removing stale model file ${file.name} (${file.length()} bytes)")
                    file.delete()
                }
            }
        }
    }

    private fun SherpaModel.files(): List<Pair<File, Long>> = listOf(
        File(context.filesDir, encoderFileName) to encoderSize,
        File(context.filesDir, decoderFileName) to decoderSize,
        File(context.filesDir, joinerFileName) to joinerSize,
        File(context.filesDir, tokensFileName) to tokensSize,
    )

    fun isModelDownloaded(model: SherpaModel): Boolean {
        val encoder = File(context.filesDir, model.encoderFileName)
        val decoder = File(context.filesDir, model.decoderFileName)
        val joiner = File(context.filesDir, model.joinerFileName)
        val tokens = File(context.filesDir, model.tokensFileName)
        
        // Exact sizes, not just existence, so a truncated file from an interrupted
        // download is treated as "not downloaded" rather than crashing Sherpa on load
        return encoder.length() == model.encoderSize &&
            decoder.length() == model.decoderSize &&
            joiner.length() == model.joinerSize &&
            tokens.length() == model.tokensSize
    }

    suspend fun downloadModel(model: SherpaModel): Boolean = withContext(ioDispatcher) {
        if (isModelDownloaded(model)) return@withContext true

        val files = listOf(
            Triple(model.encoderUrl, model.encoderFileName, model.encoderSize),
            Triple(model.decoderUrl, model.decoderFileName, model.decoderSize),
            Triple(model.joinerUrl, model.joinerFileName, model.joinerSize),
            Triple(model.tokensUrl, model.tokensFileName, model.tokensSize),
        )
        val totalBytes = model.totalSizeBytes.toFloat()
        var bytesDone = 0L

        var success = true
        for ((url, fileName, size) in files) {
            // Skip files that are already complete (e.g. ones shared with an older version)
            if (File(context.filesDir, fileName).length() == size) {
                bytesDone += size
                continue
            }
            val doneBefore = bytesDone
            val result = downloadFile(url, fileName) { progress ->
                // Overall progress weighted by bytes, so the bar doesn't race through the
                // small files and then stall on the big encoder
                val overallProgress = (doneBefore + progress * size) / totalBytes
                _downloadProgress.value = _downloadProgress.value + (model.id to overallProgress)
            }
            bytesDone += size
            if (!result) {
                success = false
                break
            }
        }

        _downloadProgress.value = _downloadProgress.value - model.id
        if (success) {
            HarkLog.i("STTModelManager", "Downloaded model: ${model.id}")
            notifyModelStoreChanged()
        }
        success
    }

    /**
     * Downloads into "<name>.part" and only renames it to the real name once the whole file
     * has arrived. A download cut short (app killed, network drop) therefore never leaves a
     * truncated file that [isModelDownloaded] would mistake for a complete model.
     */
    private suspend fun downloadFile(url: String, fileName: String, onProgress: (Float) -> Unit): Boolean = withContext(ioDispatcher) {
        val file = File(context.filesDir, fileName)
        val partFile = File(context.filesDir, "$fileName.part")
        val request = Request.Builder().url(url).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    HarkLog.e("STTModelManager", "Download failed (${response.code}): $fileName")
                    return@withContext false
                }
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()
                var totalRead = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) onProgress(totalRead.toFloat() / totalBytes)
                        }
                    }
                }

                if (totalBytes > 0 && totalRead != totalBytes) {
                    HarkLog.e("STTModelManager", "Incomplete download for $fileName: $totalRead of $totalBytes bytes")
                    partFile.delete()
                    return@withContext false
                }
            }
            file.delete()
            if (!partFile.renameTo(file)) {
                HarkLog.e("STTModelManager", "Could not finalise $fileName")
                partFile.delete()
                return@withContext false
            }
            true
        } catch (e: Exception) {
            HarkLog.e("STTModelManager", "Failed to download file: $fileName", e)
            partFile.delete()
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
        files.forEach { File(it.path + ".part").delete() }
        
        if (anyDeleted) notifyModelStoreChanged()
        return anyDeleted
    }
}
