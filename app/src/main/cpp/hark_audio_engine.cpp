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

    // Initialize FIFO Buffer
    // Capacity should be enough to hold several bursts of data
    uint32_t fifoCapacity = static_cast<uint32_t>(framesPerBurst) * 8;
    mFifoBuffer = std::make_unique<oboe::FifoBuffer>(sizeof(float), fifoCapacity);

    // Input stream: Microphone
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

    // Output stream: Speakers
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

    result = mInStream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting input stream: %s", oboe::convertToText(result));
    }

    result = mOutStream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting output stream: %s", oboe::convertToText(result));
    }

    return true;
}

void HarkAudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();
}

void HarkAudioEngine::closeStreams() {
    if (mOutStream) {
        mOutStream->stop();
        mOutStream->close();
        mOutStream.reset();
    }
    if (mInStream) {
        mInStream->stop();
        mInStream->close();
        mInStream.reset();
    }
}

void HarkAudioEngine::setMicrophoneGain(float gain) { mGain = gain; }

void HarkAudioEngine::setNoiseSuppressionEnabled(bool enabled) {
    mIsNoiseSuppressionEnabled = enabled;
    // Noise suppression is primarily handled by the system via InputPreset::VoiceCommunication
    __android_log_print(ANDROID_LOG_INFO, TAG, "Noise suppression %s", enabled ? "enabled" : "disabled");
}

void HarkAudioEngine::setDynamicsProcessingEnabled(bool enabled) { mIsDynamicsProcessingEnabled = enabled; }

oboe::DataCallbackResult HarkAudioEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {

    if (audioStream->getDirection() == oboe::Direction::Input) {
        // Input Callback: Push data into FIFO
        mFifoBuffer->write(audioData, numFrames);
    } else {
        // Output Callback: Pull data from FIFO
        auto *outputData = static_cast<float *>(audioData);
        int32_t framesRead = mFifoBuffer->read(outputData, numFrames);

        if (framesRead > 0) {
            for (int i = 0; i < framesRead; i++) {
                // Apply gain
                outputData[i] *= mGain;

                // Soft-knee limiter (Dynamics Processing)
                if (mIsDynamicsProcessingEnabled) {
                    outputData[i] = applySoftKneeLimiter(outputData[i]);
                }
            }

            // If we read fewer frames than requested, fill the rest with silence
            if (framesRead < numFrames) {
                std::fill_n(outputData + framesRead, numFrames - framesRead, 0.0f);
            }
        } else {
            // FIFO empty, output silence
            std::fill_n(outputData, numFrames, 0.0f);
        }
    }

    return oboe::DataCallbackResult::Continue;
}

float HarkAudioEngine::applySoftKneeLimiter(float input) {
    // Simple soft-knee compressor/limiter
    const float threshold = 0.8f;
    const float kneeWidth = 0.2f;
    const float attack = 0.01f;
    const float release = 0.1f;

    float absInput = std::abs(input);

    // Envelope follower
    if (absInput > mEnvelope) {
        mEnvelope = absInput * attack + mEnvelope * (1.0f - attack);
    } else {
        mEnvelope = absInput * release + mEnvelope * (1.0f - release);
    }

    if (mEnvelope <= threshold - kneeWidth / 2.0f) {
        return input; // No compression
    } else if (mEnvelope >= threshold + kneeWidth / 2.0f) {
        // Hard limiting above the knee
        return (input > 0) ? threshold : -threshold;
    } else {
        // Soft knee region
        float diff = mEnvelope - (threshold - kneeWidth / 2.0f);
        float reduction = (diff * diff) / (2.0f * kneeWidth);
        float gain = 1.0f - reduction / mEnvelope;
        return input * gain;
    }
}

void HarkAudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Stream error: %s", oboe::convertToText(error));
}
