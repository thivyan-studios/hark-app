#include "hark_audio_engine.h"
#include <android/log.h>
#include <algorithm>

#define TAG "HarkAudioEngine"

HarkAudioEngine::HarkAudioEngine() = default;

HarkAudioEngine::~HarkAudioEngine() {
    stop();
}

bool HarkAudioEngine::start() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    
    closeStreams();

    // Input stream: Microphone
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(48000)
            ->setInputPreset(oboe::InputPreset::VoiceCommunication);

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
            ->setSampleRate(48000)
            ->setCallback(this);

    result = outBuilder.openStream(mOutStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening output stream: %s", oboe::convertToText(result));
        closeStreams();
        return false;
    }

    mOutStream->setBufferSizeInFrames(mOutStream->getFramesPerBurst() * 2);

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
void HarkAudioEngine::setNoiseSuppressionEnabled(bool enabled) { mIsNoiseSuppressionEnabled = enabled; }
void HarkAudioEngine::setDynamicsProcessingEnabled(bool enabled) { mIsDynamicsProcessingEnabled = enabled; }

oboe::DataCallbackResult HarkAudioEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {

    auto *outputData = static_cast<float *>(audioData);

    // If input stream isn't ready, just output silence
    if (!mInStream || mInStream->getState() != oboe::StreamState::Started) {
        std::fill_n(outputData, numFrames, 0.0f);
        return oboe::DataCallbackResult::Continue;
    }

    // Direct relay: Read from microphone and write to outputData
    // We use a small timeout (0) to avoid blocking the audio thread
    oboe::ResultWithValue<int32_t> framesRead = mInStream->read(outputData, numFrames, 0);

    if (framesRead && framesRead.value() > 0) {
        int32_t actualFrames = framesRead.value();

        for (int i = 0; i < actualFrames; i++) {
            // Apply gain
            outputData[i] *= mGain;

            // Simple limiter (Dynamics Processing)
            if (mIsDynamicsProcessingEnabled) {
                if (outputData[i] > 1.0f) outputData[i] = 1.0f;
                else if (outputData[i] < -1.0f) outputData[i] = -1.0f;
            }
        }

        // If we read fewer frames than requested, fill the rest with silence
        if (actualFrames < numFrames) {
            std::fill_n(outputData + actualFrames, numFrames - actualFrames, 0.0f);
        }
    } else {
        // If read failed or returned 0, output silence
        std::fill_n(outputData, numFrames, 0.0f);
    }

    return oboe::DataCallbackResult::Continue;
}
