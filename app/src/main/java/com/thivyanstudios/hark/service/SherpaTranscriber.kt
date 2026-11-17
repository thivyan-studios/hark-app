package com.thivyanstudios.hark.service

import android.content.Context
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.thivyanstudios.hark.BuildConfig
import com.thivyanstudios.hark.R
import com.thivyanstudios.hark.audio.AudioEngine
import com.thivyanstudios.hark.data.STTModelManager
import com.thivyanstudios.hark.data.UserPreferencesRepository
import com.thivyanstudios.hark.di.IoDispatcher
import com.thivyanstudios.hark.util.HarkLog
import com.thivyanstudios.hark.util.TranscriptFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Live captions: loads the Sherpa-ONNX speech model, feeds it the mic audio from the native
 * engine's transcription buffer, and publishes the running transcript as proper sentences.
 *
 * The service decides *when* this runs; this class only knows *how* to transcribe.
 * Lifecycle: [prepare] → [run] (until told to stop) → [release].
 */
class SherpaTranscriber @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audioEngine: AudioEngine,
    private val sttModelManager: STTModelManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _transcription = MutableStateFlow("")
    /** Finished sentences plus the sentence currently being spoken. */
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    // Finished sentences only; the in-progress one is added on top for display
    private val finishedSentences = StringBuilder()

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    /**
     * Loads the selected speech model. Returns false (and captions stay off) if no model is
     * downloaded or it fails to load; audio relay carries on regardless.
     */
    suspend fun prepare(): Boolean = withContext(ioDispatcher) {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()
        val model = sttModelManager.availableModels.find { it.id == prefs.selectedModelId }
            ?: sttModelManager.availableModels.first()

        if (!sttModelManager.isModelDownloaded(model)) {
            HarkLog.i(TAG, "No model downloaded, skipping Sherpa initialization.")
            return@withContext false
        }

        try {
            val config = OnlineRecognizerConfig()
            val filesDir = context.filesDir
            config.modelConfig.transducer.encoder = File(filesDir, model.encoderFileName).absolutePath
            config.modelConfig.transducer.decoder = File(filesDir, model.decoderFileName).absolutePath
            config.modelConfig.transducer.joiner = File(filesDir, model.joinerFileName).absolutePath
            config.modelConfig.tokens = File(filesDir, model.tokensFileName).absolutePath

            config.decodingMethod = "modified_beam_search"
            config.maxActivePaths = 4
            // Endpoints split speech into sentences: a long pause, or a shorter one after words
            config.enableEndpoint = true
            config.endpointConfig.rule1.minTrailingSilence = 1.5f
            config.endpointConfig.rule2.minTrailingSilence = 0.8f

            // Streaming Zipformer models work at 16 kHz. Mic audio (usually 48 kHz) is
            // resampled by Sherpa itself in acceptWaveform.
            config.featConfig.sampleRate = 16000
            config.featConfig.featureDim = 80
            config.modelConfig.numThreads = prefs.sttThreads
            config.modelConfig.debug = false

            recognizer = OnlineRecognizer(null, config).also { stream = it.createStream() }
            HarkLog.i(TAG, "Sherpa-ONNX initialized successfully with model: ${model.id}")
            true
        } catch (e: Exception) {
            HarkLog.e(TAG, "Failed to initialize Sherpa-ONNX", e)
            audioEngine.sendError(context.getString(R.string.error_stt_init_failed))
            false
        }
    }

    /**
     * Transcribes until [keepRunning] returns false (or the coroutine is cancelled). Run it on
     * a background dispatcher. Does nothing if [prepare] didn't succeed.
     */
    suspend fun run(keepRunning: () -> Boolean) {
        val currentRecognizer = recognizer ?: return
        val currentStream = stream ?: return

        val audioBuffer = FloatArray(READ_SIZE)
        finishedSentences.setLength(0)

        while (keepRunning()) {
            val read = audioEngine.readTranscriptionData(audioBuffer, 0, READ_SIZE)
            if (read > 0) {
                val samples = if (read < audioBuffer.size) audioBuffer.copyOfRange(0, read) else audioBuffer

                // Pass the audio at the rate the mic is really delivering (Sherpa resamples to
                // 16 kHz itself). Read each time: a stream restart after a Bluetooth route
                // change could reopen at a different rate.
                currentStream.acceptWaveform(samples, sampleRate = audioEngine.getTranscriptionSampleRate())
                while (currentRecognizer.isReady(currentStream)) {
                    currentRecognizer.decode(currentStream)
                }

                showPartial(currentRecognizer.getResult(currentStream).text)

                if (currentRecognizer.isEndpoint(currentStream)) {
                    finishSentence(currentRecognizer.getResult(currentStream).text)
                    currentRecognizer.reset(currentStream)
                }
            }
            delay(LOOP_DELAY_MS)
        }
    }

    /** Shows the sentence still being spoken after the finished ones. */
    private fun showPartial(rawText: String) {
        if (rawText.isBlank()) return
        val partial = TranscriptFormatter.partial(rawText)
        // Spoken words only go to the log in debug builds: the log file can be shared for
        // support, and must never contain users' conversations
        if (BuildConfig.DEBUG) HarkLog.d(TAG, "Sherpa Partial Result: '$partial'")
        val display = if (finishedSentences.isNotEmpty()) "$finishedSentences $partial" else partial
        if (display != _transcription.value) _transcription.value = display
    }

    /** A pause ended the sentence: add it, with its full stop, to the finished text. */
    private fun finishSentence(rawText: String) {
        if (BuildConfig.DEBUG) {
            HarkLog.i(TAG, "Sherpa Endpoint Detected. Final: '$rawText'")
        } else {
            HarkLog.i(TAG, "Sherpa endpoint detected (${rawText.split(' ').count { it.isNotBlank() }} words)")
        }
        if (rawText.isBlank()) return
        if (finishedSentences.isNotEmpty()) finishedSentences.append(" ")
        finishedSentences.append(TranscriptFormatter.sentence(rawText))
        // Show it straight away, rather than waiting for the next words to refresh the display
        _transcription.value = finishedSentences.toString()
    }

    /** Empties the transcript (Clear on the Transcribe screen, or when streaming stops). */
    fun clear() {
        finishedSentences.setLength(0)
        _transcription.value = ""
    }

    /** Frees the model. Only call once [run] has returned, so it's never freed mid-decode. */
    suspend fun release() = withContext(ioDispatcher) {
        recognizer?.release()
        recognizer = null
        stream = null
    }

    private companion object {
        const val TAG = "SherpaTranscriber"
        const val READ_SIZE = 1600 * 2 // up to 100 ms of audio per read
        const val LOOP_DELAY_MS = 30L // short, so captions keep up with speech
    }
}
