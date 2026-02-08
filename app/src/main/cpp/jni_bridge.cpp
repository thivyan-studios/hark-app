#include <jni.h>
#include "hark_audio_engine.h"

static HarkAudioEngine *engine = nullptr;

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeInit(JNIEnv *env, jobject thiz) {
    if (engine == nullptr) {
        engine = new HarkAudioEngine();
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeStart(JNIEnv *env, jobject thiz, jint sample_rate, jint frames_per_burst) {
    if (engine) {
        return (jboolean) engine->start(sample_rate, frames_per_burst);
    }
    return JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeStop(JNIEnv *env, jobject thiz) {
    if (engine) {
        engine->stop();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetMicrophoneGain(JNIEnv *env, jobject thiz,
                                                                      jfloat gain) {
    if (engine) {
        engine->setMicrophoneGain(gain);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetNoiseSuppressionEnabled(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jboolean enabled) {
    if (engine) {
        engine->setNoiseSuppressionEnabled(enabled);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetDynamicsProcessingEnabled(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jboolean enabled) {
    if (engine) {
        engine->setDynamicsProcessingEnabled(enabled);
    }
}
