#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper/whisper.h"

#define LOG_TAG "WhisperBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static struct whisper_context * g_whisper_ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeInitWhisper(JNIEnv *env, jobject thiz, jstring model_path) {
    if (g_whisper_ctx != nullptr) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
    }

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading Whisper model from: %s", path);

    g_whisper_ctx = whisper_init_from_file(path);

    env->ReleaseStringUTFChars(model_path, path);

    if (g_whisper_ctx == nullptr) {
        LOGE("Failed to initialize Whisper context");
        return JNI_FALSE;
    }

    LOGI("Whisper model loaded successfully");
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeTranscribe(JNIEnv *env, jobject thiz, jfloatArray audio_data) {
    if (g_whisper_ctx == nullptr) {
        return env->NewStringUTF("ERROR: Whisper not initialized");
    }

    jsize len = env->GetArrayLength(audio_data);
    float * p_audio = env->GetFloatArrayElements(audio_data, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = 4;

    LOGI("Transcribing %d samples...", len);

    if (whisper_full(g_whisper_ctx, params, p_audio, len) != 0) {
        LOGE("Failed to transcribe audio");
        env->ReleaseFloatArrayElements(audio_data, p_audio, 0);
        return env->NewStringUTF("ERROR: Transcription failed");
    }

    std::string result_text = "";
    int n_segments = whisper_full_n_segments(g_whisper_ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char * text = whisper_full_get_segment_text(g_whisper_ctx, i);
        result_text += text;
    }

    env->ReleaseFloatArrayElements(audio_data, p_audio, 0);
    return env->NewStringUTF(result_text.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeReleaseWhisper(JNIEnv *env, jobject thiz) {
    if (g_whisper_ctx != nullptr) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
        LOGI("Whisper context released");
    }
}
