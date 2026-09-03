#include <jni.h>
#include "hark_audio_engine.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeCreate(JNIEnv *env, jobject thiz) {
    return reinterpret_cast<jlong>(new HarkAudioEngine());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeDelete(JNIEnv *env, jobject thiz, jlong handle) {
    delete reinterpret_cast<HarkAudioEngine *>(handle);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeStart(JNIEnv *env, jobject thiz, jlong handle, jint sample_rate, jint frames_per_burst) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        return (jboolean) engine->start(sample_rate, frames_per_burst);
    }
    return JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeStop(JNIEnv *env, jobject thiz, jlong handle) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        engine->stop();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetMicrophoneGain(JNIEnv *env, jobject thiz, jlong handle, jfloat gain) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        engine->setMicrophoneGain(gain);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetAmbientGain(JNIEnv *env, jobject thiz, jlong handle, jfloat gain) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        engine->setAmbientGain(gain);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetNoiseSuppressionEnabled(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        engine->setNoiseSuppressionEnabled(enabled);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetDynamicsProcessingEnabled(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        engine->setDynamicsProcessingEnabled(enabled);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetTranscriptModeEnabled(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        engine->setTranscriptModeEnabled(enabled);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeSetTrebleBoostEnabled(JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        engine->setTrebleBoostEnabled(enabled);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeReadTranscriptionData(JNIEnv *env, jobject thiz, jlong handle, jfloatArray target, jint offset, jint num_frames) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
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

extern "C"
JNIEXPORT jfloat JNICALL
Java_com_thivyanstudios_hark_audio_AudioEngine_nativeGetTranscriptionLevel(JNIEnv *env, jobject thiz, jlong handle) {
    auto *engine = reinterpret_cast<HarkAudioEngine *>(handle);
    if (engine) {
        return engine->getTranscriptionLevel();
    }
    return 0.0f;
}
