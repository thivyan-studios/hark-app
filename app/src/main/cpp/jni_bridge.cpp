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
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetAmbientGain(JNIEnv *env, jobject thiz,
                                                                   jfloat gain) {
    if (engine) {
        engine->setAmbientGain(gain);
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

extern "C"
JNIEXPORT jint JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeReadTranscriptionData(JNIEnv *env, jobject thiz,
                                                                         jfloatArray target,
                                                                         jint offset,
                                                                         jint num_frames) {
    if (engine) {
        jsize arrayLen = env->GetArrayLength(target);
        if (offset + num_frames > arrayLen) {
            num_frames = arrayLen - offset;
        }
        if (num_frames <= 0) return 0;

        float *buffer = env->GetFloatArrayElements(target, nullptr);
        int32_t framesRead = engine->readTranscriptionData(buffer + offset, num_frames);
        env->ReleaseFloatArrayElements(target, buffer, 0);
        return (jint) framesRead;
    }
    return 0;
}
