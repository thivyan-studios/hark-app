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
    for (auto & mEqBand : mEqBands) {
        mEqBand.reset();
    }
    mNoiseGate.reset();
}

bool HarkAudioEngine::start() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();
    resetDsp();
    updateFilters();

    bool isTestMode = mIsTestMode.load();

    // 1. Setup Input Stream
    if (!isTestMode) {
        oboe::AudioStreamBuilder inBuilder;
        inBuilder.setDirection(oboe::Direction::Input)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::Float)
                ->setChannelCount(oboe::ChannelCount::Mono)
                ->setSampleRate(mSampleRate)
                // Using Unprocessed minimizes HAL-level latency by bypassing system AEC/NS
                ->setInputPreset(oboe::InputPreset::Unprocessed)
                ->setCallback(this); // Using callback for input too

        oboe::Result result = inBuilder.openStream(mInStream);
        if (result != oboe::Result::OK) return false;
    }

    // 2. Setup Output Stream
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(mSampleRate)
            ->setUsage(oboe::Usage::VoiceCommunication)
            ->setContentType(oboe::ContentType::Speech)
            ->setCallback(this);

    oboe::Result result = outBuilder.openStream(mOutStream);
    if (result != oboe::Result::OK) {
        closeStreams();
        return false;
    }

    // Set buffer size to exactly 2 bursts for a balance of stability and ultra-low latency
    // AAudio MMAP path usually requires 2 bursts.
    mOutStream->setBufferSizeInFrames(mFramesPerBurst * 2);

    // Initialize the lock-free FIFO
    mFifo = std::make_unique<oboe::FifoBuffer>(sizeof(float), mFramesPerBurst * 8); 

    if (mInStream) mInStream->requestStart();
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

void HarkAudioEngine::updateFilters() {
    // Implementation for updateFilters
}

void HarkAudioEngine::setMicrophoneGain(float gain) {
    mGain.store(gain);
}

void HarkAudioEngine::setNoiseSuppressionEnabled(bool enabled) {
    mIsNoiseSuppressionEnabled.store(enabled);
}

void HarkAudioEngine::setDynamicsProcessingEnabled(bool enabled) {
    mIsDynamicsProcessingEnabled.store(enabled);
}

void HarkAudioEngine::setEqualizerBands(const float* gains, int numBands) {
    // Implementation for setEqualizerBands
}

void HarkAudioEngine::setTestMode(bool enabled) {
    mIsTestMode.store(enabled);
}

oboe::DataCallbackResult HarkAudioEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    auto *data = static_cast<float *>(audioData);

    if (audioStream->getDirection() == oboe::Direction::Input) {
        // --- INPUT CALLBACK ---
        // Push raw input into FIFO as fast as possible
        if (mFifo) {
            mFifo->write(data, numFrames);
        }
        return oboe::DataCallbackResult::Continue;
    } else {
        // --- OUTPUT CALLBACK ---
        for (auto & mEqBand : mEqBands) {
            mEqBand.updateFromPending();
        }

        if (mIsTestMode.load(std::memory_order_relaxed)) {
            mPinkNoiseGenerator.fillBuffer(data, numFrames);
        } else {
            // Pull from FIFO
            int32_t framesRead = 0;
            if (mFifo) {
                framesRead = mFifo->read(data, numFrames);
            }
            
            if (framesRead < numFrames) {
                // Underflow: fill remaining with silence
                std::fill_n(data + framesRead, numFrames - framesRead, 0.0f);
            }
        }

        // Process DSP on the output buffer (In-place)
        for (int i = 0; i < numFrames; i++) {
            data[i] = processSample(data[i]);
        }

        return oboe::DataCallbackResult::Continue;
    }
}

float HarkAudioEngine::processSample(float sample) {
    float output = sample * mGain.load(std::memory_order_relaxed);

    for (auto & mEqBand : mEqBands) {
        output = mEqBand.process(output);
    }

    if (mIsNoiseSuppressionEnabled.load(std::memory_order_relaxed)) {
        output = mNoiseGate.process(output);
    }

    if (mIsDynamicsProcessingEnabled.load(std::memory_order_relaxed)) {
        // Faster soft-clipping instead of hard clamp for better audio quality at low latency
        output = std::tanh(output); 
    }

    return output;
}
