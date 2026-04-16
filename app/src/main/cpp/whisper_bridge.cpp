#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sys/stat.h>
#include <fstream>
#include <mutex>
#include "whisper/whisper.h"
#include "whisper/ggml.h"
#include "whisper/ggml-cpu.h"

#define LOG_TAG "WhisperBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_whisper_mutex;
static struct whisper_context * g_whisper_ctx = nullptr;
static std::vector<char> g_model_buffer;

// Custom log callback to pipe Whisper logs to Logcat
void whisper_log_callback(enum ggml_log_level level, const char * text, void * user_data) {
    (void)level;
    (void)user_data;
    // Lower priority for internal logs to reduce noise
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Whisper Internal: %s", text);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeInitWhisper(JNIEnv *env, jobject thiz, jstring model_path) {
    std::lock_guard<std::mutex> lock(g_whisper_mutex);

    if (g_whisper_ctx != nullptr) {
        LOGI("Re-initializing Whisper: Freeing existing context");
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
        g_model_buffer.clear();
    }

    whisper_log_set(whisper_log_callback, nullptr);

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading Whisper model: %s", path);

    // Read file into buffer to avoid mmap alignment issues on Android
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open model file");
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    g_model_buffer.resize(size);
    if (!file.read(g_model_buffer.data(), size)) {
        LOGE("Failed to read model file into buffer");
        g_model_buffer.clear();
        env->ReleaseStringUTFChars(model_path, path);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(model_path, path);

    LOGI("Model buffer size: %lld bytes. Initializing context...", (long long)size);

    // Use whisper_init_from_buffer_with_params instead of deprecated whisper_init_from_buffer
    // IMPORTANT: The buffer MUST remain valid for the entire lifetime of the context
    whisper_context_params cparams = whisper_context_default_params();
    g_whisper_ctx = whisper_init_from_buffer_with_params(g_model_buffer.data(), g_model_buffer.size(), cparams);

    if (g_whisper_ctx == nullptr) {
        LOGE("Failed to initialize Whisper context from buffer");
        g_model_buffer.clear();
        return JNI_FALSE;
    }

    LOGI("Whisper model loaded successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeTranscribe(JNIEnv *env, jobject thiz, jfloatArray audio_data, jint len) {
    std::lock_guard<std::mutex> lock(g_whisper_mutex);

    if (g_whisper_ctx == nullptr) {
        return env->NewStringUTF("ERROR: Whisper not initialized");
    }

    if (len <= 0) {
        return env->NewStringUTF("[EMPTY]");
    }

    float * p_audio = env->GetFloatArrayElements(audio_data, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = 4;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.no_context = true;
    params.single_segment = true;

    // Voice activity detection settings
    params.no_speech_thold = 0.5f; // More aggressive
    params.entropy_thold = 2.4f;

    // Basic audio signal check
    float max_abs = 0.0f;
    for (int i = 0; i < len; ++i) {
        float abs_s = std::abs(p_audio[i]);
        if (abs_s > max_abs) max_abs = abs_s;
    }

    // Normalization / AGC
    if (max_abs > 0.0001f) {
        float target_max = 0.8f;
        float gain = target_max / max_abs;
        if (gain > 4000.0f) gain = 4000.0f;

        if (gain > 1.05f || gain < 0.95f) {
            for (int i = 0; i < len; ++i) {
                p_audio[i] *= gain;
            }
        }
    } else {
        LOGI("Signal too weak (max_abs %.8f), silence detected.", max_abs);
        env->ReleaseFloatArrayElements(audio_data, p_audio, 0);
        return env->NewStringUTF("[SILENCE]");
    }

    int64_t t_start = ggml_time_ms();
    int ret = whisper_full(g_whisper_ctx, params, p_audio, len);
    int64_t t_end = ggml_time_ms();

    LOGI("whisper_full returned %d in %lld ms", ret, (long long)(t_end - t_start));

    if (ret != 0) {
        LOGE("Failed to transcribe audio, error code: %d", ret);
        env->ReleaseFloatArrayElements(audio_data, p_audio, 0);
        return env->NewStringUTF("ERROR: Transcription failed");
    }

    std::string result_text;
    int n_segments = whisper_full_n_segments(g_whisper_ctx);

    for (int i = 0; i < n_segments; ++i) {
        const char * text = whisper_full_get_segment_text(g_whisper_ctx, i);
        if (text) {
            result_text += text;
        }
    }

    env->ReleaseFloatArrayElements(audio_data, p_audio, 0);

    if (result_text.empty()) {
        return env->NewStringUTF("[NO_RESULT]");
    }

    return env->NewStringUTF(result_text.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeReleaseWhisper(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_whisper_mutex);
    if (g_whisper_ctx != nullptr) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
        g_model_buffer.clear();
        LOGI("Whisper context and buffer released");
    }
}


