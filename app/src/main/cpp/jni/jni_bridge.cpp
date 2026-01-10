#include <jni.h>
#include <memory>
#include <vector>
#include <android/log.h>
#include "../engine/hark_audio_engine.h"

#define TAG "HarkJni"

static std::unique_ptr<HarkAudioEngine> gEngine = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeInit(JNIEnv *env, jobject thiz, 
                                                         jint sampleRate, jint framesPerBurst) {
    if (!gEngine) {
        gEngine = std::make_unique<HarkAudioEngine>(sampleRate, framesPerBurst);
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Engine Initialized: SR=%d, FPB=%d", sampleRate, framesPerBurst);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeDestroy(JNIEnv *env, jobject thiz) {
    if (gEngine) {
        gEngine.reset();
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Engine Destroyed");
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeStart(JNIEnv *env, jobject thiz) {
    if (gEngine) {
        bool result = gEngine->start();
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Engine Start: %s", result ? "Success" : "Failed");
        return static_cast<jboolean>(result);
    }
    return JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeStop(JNIEnv *env, jobject thiz) {
    if (gEngine) {
        gEngine->stop();
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Engine Stop");
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetMicrophoneGain(JNIEnv *env, jobject thiz,
                                                                      jfloat gain) {
    if (gEngine) {
        gEngine->setMicrophoneGain(gain);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetNoiseSuppressionEnabled(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jboolean enabled) {
    if (gEngine) {
        gEngine->setNoiseSuppressionEnabled(enabled);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetDynamicsProcessingEnabled(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jboolean enabled) {
    if (gEngine) {
        gEngine->setDynamicsProcessingEnabled(enabled);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetEqualizerBands(JNIEnv *env, jobject thiz,
                                                                      jfloatArray bands) {
    if (gEngine && bands) {
        jsize len = env->GetArrayLength(bands);
        jfloat *body = env->GetFloatArrayElements(bands, nullptr);
        
        if (body) {
            gEngine->setEqualizerBands(body, static_cast<int>(len));
            env->ReleaseFloatArrayElements(bands, body, JNI_ABORT);
        }
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetTestMode(JNIEnv *env, jobject thiz,
                                                                jboolean enabled) {
    if (gEngine) {
        gEngine->setTestMode(enabled);
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "Test Mode: %s", enabled ? "ON" : "OFF");
    }
}
