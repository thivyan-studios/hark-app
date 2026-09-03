#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <sys/stat.h>
#include <fstream>
#include <mutex>
#include <cmath>
#include <algorithm>
#include "whisper/whisper.h"
#include "whisper/ggml.h"
#include "whisper/ggml-cpu.h"

#define LOG_TAG "WhisperBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_whisper_mutex;
static struct whisper_context * g_whisper_ctx = nullptr;

// Custom log callback to pipe Whisper logs to Logcat
void whisper_log_callback(enum ggml_log_level level, const char * text, void * user_data) {
    (void)level;
    (void)user_data;
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
    }

    whisper_log_set(whisper_log_callback, nullptr);

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading Whisper model: %s", path);

    whisper_context_params cparams = whisper_context_default_params();
    // whisper.cpp will handle the file reading and potentially use mmap for efficiency
    g_whisper_ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(model_path, path);

    if (g_whisper_ctx == nullptr) {
        LOGE("Failed to initialize Whisper context from file");
        return JNI_FALSE;
    }

    LOGI("Whisper model loaded successfully");
    return JNI_TRUE;
}

// Helper to check for common hallucination strings
bool is_hallucination(const std::string& text) {
    static const std::vector<std::string> junk = {
        "thanks for watching", "thank you for watching", "subtitles by", "please subscribe",
        "repro", "mbc", "bye", "[music]", "♪", "thank you", "thanks", "thank you.", "thank you for",
        "watch", "watching", "subscribe", "subtitles"
    };

    std::string lower = text;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);

    // Trim punctuation and whitespace
    lower.erase(std::remove_if(lower.begin(), lower.end(), [](char c) {
        return std::isspace(c) || std::ispunct(c);
    }), lower.end());

    if (lower.empty() || lower.length() <= 1) return true; // Ignore single chars or empty

    for (const auto& j : junk) {
        std::string clean_j = j;
        clean_j.erase(std::remove_if(clean_j.begin(), clean_j.end(), [](char c) {
            return std::isspace(c) || std::ispunct(c);
        }), clean_j.end());

        if (lower == clean_j) return true;
    }
    return false;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeTranscribe(JNIEnv *env, jobject thiz, jfloatArray audio_data, jint len, jint threads, jstring language, jboolean translate) {
    std::lock_guard<std::mutex> lock(g_whisper_mutex);

    if (g_whisper_ctx == nullptr) {
        return env->NewStringUTF("ERROR: Whisper not initialized");
    }

    if (len <= 0) {
        return env->NewStringUTF("[EMPTY]");
    }

    const char * lang_str = env->GetStringUTFChars(language, nullptr);
    std::string lang(lang_str);
    env->ReleaseStringUTFChars(language, lang_str);

    float * p_audio = env->GetFloatArrayElements(audio_data, nullptr);

    // AGGRESSIVE Stability Settings
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    // Fix: Handle non-multilingual models. English-only models (.en) do not support auto-detection or translation.
    bool is_multilingual = whisper_is_multilingual(g_whisper_ctx) != 0;
    if (is_multilingual) {
        params.language = lang.c_str();
        params.translate = (bool) translate;
    } else {
        params.language = "en";
        params.translate = false;
        if (lang != "en" && lang != "auto") {
            LOGI("Model is English-only, forcing language to 'en' (was '%s')", lang.c_str());
        }
    }

    params.n_threads = (int) threads;

    params.suppress_blank = true;
    params.suppress_nst = true;
    params.no_context = true;
    params.single_segment = true;

    params.temperature = 0.0f;
    params.no_speech_thold = 0.3f; // Less aggressive VAD
    params.entropy_thold = 2.4f;
    params.logprob_thold = -1.0f; // Reject low-confidence results

    // Disable any internal state/context to prevent loops
    params.max_tokens = 64;

    // Audio signal check
    float sum_sq = 0.0f;
    for (int i = 0; i < len; ++i) {
        sum_sq += p_audio[i] * p_audio[i];
    }
    float rms = std::sqrt(sum_sq / len);

    // Very low silence threshold to catch faint voices
    if (rms < 0.0005f) {
        LOGI("Signal RMS too low (%.5f), skipping transcription.", rms);
        // Use JNI_ABORT as we didn't modify the array (or don't need to commit changes back)
        env->ReleaseFloatArrayElements(audio_data, p_audio, JNI_ABORT);
        return env->NewStringUTF("[SILENCE]");
    }

    // Conservative AGC
    float max_abs = 0.0f;
    for (int i = 0; i < len; ++i) {
        float abs_s = std::abs(p_audio[i]);
        if (abs_s > max_abs) max_abs = abs_s;
    }

    if (max_abs > 0.0f) {
        float target = 0.6f;
        float gain = target / max_abs;
        if (gain > 15.0f) gain = 15.0f; // Limit amplification of floor noise
        for (int i = 0; i < len; ++i) p_audio[i] *= gain;
    }

    int64_t t_start = ggml_time_ms();
    int ret = whisper_full(g_whisper_ctx, params, p_audio, len);
    int64_t t_end = ggml_time_ms();

    LOGI("whisper_full ret %d in %lld ms (RMS: %.5f)", ret, (long long)(t_end - t_start), rms);

    if (ret != 0) {
        // Use JNI_ABORT to avoid unnecessary copy-back to Java
        env->ReleaseFloatArrayElements(audio_data, p_audio, JNI_ABORT);
        return env->NewStringUTF("ERROR: Transcription failed");
    }

    std::string result_text;
    int n_segments = whisper_full_n_segments(g_whisper_ctx);
    for (int i = 0; i < n_segments; ++i) {
        float prob = whisper_full_get_segment_no_speech_prob(g_whisper_ctx, i);
        if (prob > 0.80f) {
            LOGI("Segment %d rejected by no_speech_prob: %.3f", i, prob);
            continue;
        }

        const char * text = whisper_full_get_segment_text(g_whisper_ctx, i);
        if (text && !is_hallucination(text)) {
            result_text += text;
        } else if (text) {
            LOGI("Filtered hallucination: %s", text);
        }
    }

    // Use JNI_ABORT to avoid unnecessary copy-back to Java
    env->ReleaseFloatArrayElements(audio_data, p_audio, JNI_ABORT);

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
        LOGI("Whisper context released");
    }
}
