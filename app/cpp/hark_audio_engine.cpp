#include "hark_audio_engine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <oboe/FifoBuffer.h>

#define TAG "HarkAudioEngine"

HarkAudioEngine::HarkAudioEngine() = default;

HarkAudioEngine::~HarkAudioEngine() {
    stop();
}

bool HarkAudioEngine::start(int32_t sampleRate, int32_t framesPerBurst) {
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();

    uint32_t fifoCapacity = static_cast<uint32_t>(framesPerBurst) * 8;
    mFifoBuffer = std::make_unique<oboe::FifoBuffer>(sizeof(float), fifoCapacity);

    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sampleRate)
            ->setInputPreset(oboe::InputPreset::VoiceCommunication)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    oboe::Result result = inBuilder.openStream(mInStream);
    if (result != oboe::Result::OK) return false;

    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sampleRate)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    result = outBuilder.openStream(mOutStream);
    if (result != oboe::Result::OK) {
        closeStreams();
        return false;
    }

    mOutStream->setBufferSizeInFrames(framesPerBurst * 2);
    mInStream->requestStart();
    mOutStream->requestStart();

    return true;
}

void HarkAudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();
}

void HarkAudioEngine::closeStreams() {
    if (mOutStream) { mOutStream->stop(); mOutStream->close(); mOutStream.reset(); }
    if (mInStream) { mInStream->stop(); mInStream->close(); mInStream.reset(); }
}

void HarkAudioEngine::setMicrophoneGain(float gain) { mGain.store(gain); }
void HarkAudioEngine::setAmbientGain(float gain) { mAmbientGain.store(gain); }
void HarkAudioEngine::setNoiseSuppressionEnabled(bool enabled) { mIsNoiseSuppressionEnabled.store(enabled); }
void HarkAudioEngine::setDynamicsProcessingEnabled(bool enabled) { mIsDynamicsProcessingEnabled.store(enabled); }

oboe::DataCallbackResult HarkAudioEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {

    if (audioStream->getDirection() == oboe::Direction::Input) {
        mFifoBuffer->write(audioData, numFrames);
    } else {
        auto *outputData = static_cast<float *>(audioData);
        int32_t framesRead = mFifoBuffer->read(outputData, numFrames);

        float currentGain = mGain.load();
        float currentAmbientGain = mAmbientGain.load();

        if (framesRead > 0) {
            for (int i = 0; i < framesRead; i++) {
                float rawSample = outputData[i];

                // 1. PRIMARY PATH (The "Hearing Aid" stream)
                // This is where heavy DSP happens.
                float processedSample = rawSample;

                // Example: Basic Speech Enhancement (High-pass filter simulation)
                // In a production app, you would insert a FFT or specialized NS algorithm here.
                if (mIsNoiseSuppressionEnabled.load()) {
                    processedSample = applySpeechEnhancement(rawSample);
                }

                float primarySignal = processedSample * currentGain;

                // 2. AMBIENT PATH (The "Transparency" stream)
                // This stays raw so the user can hear the "natural" environment.
                float ambientSignal = rawSample * currentAmbientGain;

                // 3. MIXER: Combine processed stream + raw environment
                float mixedSignal = primarySignal + ambientSignal;

                // 4. SAFETY: Final limiter to prevent clipping
                outputData[i] = applySoftKneeLimiter(mixedSignal);
            }

            if (framesRead < numFrames) {
                std::fill_n(outputData + framesRead, numFrames - framesRead, 0.0f);
            }
        } else {
            std::fill_n(outputData, numFrames, 0.0f);
        }
    }
    return oboe::DataCallbackResult::Continue;
}

// Placeholder for real DSP - currently a simple high-pass to clarify speech
float HarkAudioEngine::applySpeechEnhancement(float input) {
    static float prevInput = 0.0f;
    static float prevOutput = 0.0f;
    // Simple DC-offset / High-pass filter to reduce low-end rumble
    float output = input - prevInput + 0.95f * prevOutput;
    prevInput = input;
    prevOutput = output;
    return output;
}

float HarkAudioEngine::applySoftKneeLimiter(float input) {
    const float threshold = 0.8f;
    const float kneeWidth = 0.2f;
    const float attack = 0.01f;
    const float release = 0.1f;
    float absInput = std::abs(input);

    if (absInput > mEnvelope) {
        mEnvelope = absInput * attack + mEnvelope * (1.0f - attack);
    } else {
        mEnvelope = absInput * release + mEnvelope * (1.0f - release);
    }

    if (mEnvelope <= threshold - kneeWidth / 2.0f) return input;
    if (mEnvelope >= threshold + kneeWidth / 2.0f) return (input > 0) ? threshold : -threshold;

    float diff = mEnvelope - (threshold - kneeWidth / 2.0f);
    float reduction = (diff * diff) / (2.0f * kneeWidth);
    return input * (1.0f - reduction / mEnvelope);
}

void HarkAudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Stream error: %s", oboe::convertToText(error));
}
