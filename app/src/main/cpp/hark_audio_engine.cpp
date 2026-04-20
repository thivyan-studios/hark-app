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

    mSampleRate = sampleRate;
    mResampleAccumulator = 0;

    uint32_t fifoCapacity = static_cast<uint32_t>(framesPerBurst) * 8;
    mFifoBuffer = std::make_unique<oboe::FifoBuffer>(sizeof(float), fifoCapacity);

    // Whisper.cpp usually expects 16kHz audio.
    // 16000 * 10 seconds = 160000 samples capacity.
    mTranscriptionFifo = std::make_unique<oboe::FifoBuffer>(sizeof(float), 160000);

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
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening input stream: %s", oboe::convertToText(result));
        return false;
    }

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
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening output stream: %s", oboe::convertToText(result));
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
    mTranscriptionFifo.reset();
}

int32_t HarkAudioEngine::readTranscriptionData(float *target, int32_t numFrames) {
    if (!mTranscriptionFifo) return 0;
    return mTranscriptionFifo->read(target, numFrames);
}

float HarkAudioEngine::getTranscriptionLevel() {
    return mTranscriptionLevel.load(std::memory_order_acquire);
}

void HarkAudioEngine::setMicrophoneGain(float gain) { mGain.store(gain, std::memory_order_release); }
void HarkAudioEngine::setAmbientGain(float gain) { mAmbientGain.store(gain, std::memory_order_release); }
void HarkAudioEngine::setNoiseSuppressionEnabled(bool enabled) {
    mIsNoiseSuppressionEnabled.store(enabled, std::memory_order_release);
}
void HarkAudioEngine::setDynamicsProcessingEnabled(bool enabled) {
    mIsDynamicsProcessingEnabled.store(enabled, std::memory_order_release);
}
void HarkAudioEngine::setTranscriptModeEnabled(bool enabled) {
    mIsTranscriptModeEnabled.store(enabled, std::memory_order_release);
}

oboe::DataCallbackResult HarkAudioEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {

    if (audioStream->getDirection() == oboe::Direction::Input) {
        mFifoBuffer->write(audioData, numFrames);

        // Apply gain to transcription data as well so Whisper gets a usable signal
        float currentGain = mGain.load(std::memory_order_acquire);
        auto *inputData = static_cast<const float*>(audioData);

        // Boost gain specifically for transcription on emulators if needed
        float transcriptionGain = currentGain * 10.0f;

        float sumSquares = 0.0f;
        std::vector<float> gainedData(numFrames);
        for (int i = 0; i < numFrames; ++i) {
            float sample = inputData[i] * transcriptionGain;
            gainedData[i] = sample;
            sumSquares += sample * sample;
        }

        // Calculate RMS level for visualization/gating
        float rms = std::sqrt(sumSquares / static_cast<float>(numFrames));

        // Exponential moving average for smoothing the level
        float alpha = 0.1f;
        float currentLevel = mTranscriptionLevel.load(std::memory_order_acquire);
        mTranscriptionLevel.store(currentLevel * (1.0f - alpha) + rms * alpha, std::memory_order_release);

        pushToTranscriptionFifo(gainedData.data(), numFrames);
    } else {
        auto *outputData = static_cast<float *>(audioData);

        if (mIsTranscriptModeEnabled.load(std::memory_order_acquire)) {
            std::fill_n(outputData, numFrames, 0.0f);
            return oboe::DataCallbackResult::Continue;
        }

        int32_t framesRead = mFifoBuffer->read(outputData, numFrames);

        float currentGain = mGain.load(std::memory_order_acquire);
        float currentAmbientGain = mAmbientGain.load(std::memory_order_acquire);
        bool dynamicsEnabled = mIsDynamicsProcessingEnabled.load(std::memory_order_acquire);
        bool nsEnabled = mIsNoiseSuppressionEnabled.load(std::memory_order_acquire);

        if (framesRead > 0) {
            for (int i = 0; i < framesRead; i++) {
                float rawSample = outputData[i];

                // 1. PRIMARY PATH (The "Hearing Aid" stream)
                float processedSample = rawSample;
                if (nsEnabled) {
                    processedSample = applySpeechEnhancement(rawSample);
                }
                float primarySignal = processedSample * currentGain;

                // 2. AMBIENT PATH (The "Transparency" stream)
                float ambientSignal = rawSample * currentAmbientGain;

                // 3. MIXER
                float mixedSignal = primarySignal + ambientSignal;

                // 4. SAFETY: Limiter
                if (dynamicsEnabled) {
                    outputData[i] = applySoftKneeLimiter(mixedSignal);
                } else {
                    outputData[i] = std::clamp(mixedSignal, -1.0f, 1.0f);
                }
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

void HarkAudioEngine::pushToTranscriptionFifo(const float* data, int32_t numFrames) {
    if (!mTranscriptionFifo || !data || numFrames <= 0) return;

    double skip = (double)mSampleRate / 16000.0;
    int32_t resampledCount = 0;

    while (mResampleAccumulator < (double)numFrames) {
        int index = (int)mResampleAccumulator;
        if (index >= 0 && index < numFrames) {
            mResampleBuffer[resampledCount++] = data[index];
            if (resampledCount >= 2048) break; // Safety break
        }
        mResampleAccumulator += skip;
    }
    mResampleAccumulator -= (double)numFrames;

    if (resampledCount > 0) {
        int32_t written = mTranscriptionFifo->write(mResampleBuffer, resampledCount);
        if (written < resampledCount) {
            // FIFO overflow - clear it to avoid stale data
            float dummy;
            while(mTranscriptionFifo->read(&dummy, 1) > 0);
        }
    }
}


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
