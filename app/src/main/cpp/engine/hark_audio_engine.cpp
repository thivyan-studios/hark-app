#include "hark_audio_engine.h"
#include "../dsp/dsp_utils.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>

#define TAG "HarkAudioEngine"

HarkAudioEngine::HarkAudioEngine(int32_t sampleRate, int32_t framesPerBurst) 
    : mSampleRate(sampleRate), mFramesPerBurst(framesPerBurst) {
    resetDsp();
}

HarkAudioEngine::~HarkAudioEngine() {
    stop();
}

void HarkAudioEngine::resetDsp() {
    for (int i = 0; i < kNumEqBands; i++) {
        mEqBands[i].reset();
    }
    mNoiseGate.reset();
}

bool HarkAudioEngine::start() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();
    resetDsp();
    updateFilters();

    bool isTestMode = mIsTestMode.load();

    if (!isTestMode) {
        oboe::AudioStreamBuilder inBuilder;
        inBuilder.setDirection(oboe::Direction::Input)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(oboe::ChannelCount::Mono)
                ->setSampleRate(mSampleRate)
                ->setInputPreset(oboe::InputPreset::VoiceCommunication);

        oboe::Result result = inBuilder.openStream(mInStream);
        if (result != oboe::Result::OK) return false;
        mInStream->requestStart();
    } else {
        mPinkNoiseGenerator.reset();
    }

    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(mSampleRate)
            ->setCallback(this);

    oboe::Result result = outBuilder.openStream(mOutStream);
    if (result != oboe::Result::OK) {
        closeStreams();
        return false;
    }

    mOutStream->setBufferSizeInFrames(mFramesPerBurst * 2);
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
void HarkAudioEngine::setNoiseSuppressionEnabled(bool enabled) { mIsNoiseSuppressionEnabled.store(enabled); }
void HarkAudioEngine::setDynamicsProcessingEnabled(bool enabled) { mIsDynamicsProcessingEnabled.store(enabled); }
void HarkAudioEngine::setTestMode(bool enabled) { mIsTestMode.store(enabled); }

void HarkAudioEngine::setEqualizerBands(const float* gains, int numBands) {
    for (int i = 0; i < std::min(numBands, kNumEqBands); i++) {
        mLastGains[i] = gains[i];
    }
    updateFilters();
}

void HarkAudioEngine::updateFilters() {
    const float freqs[] = {60.0f, 230.0f, 910.0f, 3000.0f, 10000.0f}; 
    const float fs = (float)mSampleRate;

    for (int i = 0; i < kNumEqBands; i++) {
        BiquadCoefficients coeffs = DspUtils::calculatePeakingEq(freqs[i], fs, mLastGains[i]);
        mEqBands[i].setCoefficients(coeffs);
    }
}

float HarkAudioEngine::processSample(float sample) {
    float output = sample * mGain.load(std::memory_order_relaxed);

    for (int i = 0; i < kNumEqBands; i++) {
        output = mEqBands[i].process(output);
    }

    if (mIsNoiseSuppressionEnabled.load(std::memory_order_relaxed)) {
        output = mNoiseGate.process(output);
    }

    if (mIsDynamicsProcessingEnabled.load(std::memory_order_relaxed)) {
        output = std::clamp(output, -1.0f, 1.0f);
    }

    return output;
}

oboe::DataCallbackResult HarkAudioEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    for (int i = 0; i < kNumEqBands; i++) {
        mEqBands[i].updateFromPending();
    }

    auto *outputData = static_cast<float *>(audioData);
    
    if (mIsTestMode.load(std::memory_order_relaxed)) {
        mPinkNoiseGenerator.fillBuffer(outputData, numFrames);
        for (int i = 0; i < numFrames; i++) {
            outputData[i] = processSample(outputData[i]);
        }
        return oboe::DataCallbackResult::Continue;
    }

    if (!mStreamLock.try_lock()) {
        std::fill_n(outputData, numFrames, 0.0f);
        return oboe::DataCallbackResult::Continue;
    }

    if (!mInStream || mInStream->getState() != oboe::StreamState::Started) {
        mStreamLock.unlock();
        std::fill_n(outputData, numFrames, 0.0f);
        return oboe::DataCallbackResult::Continue;
    }

    oboe::ResultWithValue<int32_t> framesRead = mInStream->read(outputData, numFrames, 0);
    mStreamLock.unlock();

    if (framesRead && framesRead.value() > 0) {
        for (int i = 0; i < framesRead.value(); i++) {
            outputData[i] = processSample(outputData[i]);
        }
        if (framesRead.value() < numFrames) {
            std::fill_n(outputData + framesRead.value(), numFrames - framesRead.value(), 0.0f);
        }
    } else {
        std::fill_n(outputData, numFrames, 0.0f);
    }
    return oboe::DataCallbackResult::Continue;
}
