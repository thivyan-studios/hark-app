#include "hark_audio_engine.h"
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <oboe/FifoBuffer.h>

#define TAG "HarkAudioEngine"

HarkAudioEngine::HarkAudioEngine() = default;

HarkAudioEngine::~HarkAudioEngine() {
    stop();
}

bool HarkAudioEngine::start(int32_t sampleRate, int32_t framesPerBurst) {
    mShouldBeStreaming.store(false, std::memory_order_release);
    joinRestartThread();

    std::lock_guard<std::mutex> lock(mStreamLock);
    mSampleRate = sampleRate;
    mFramesPerBurst = framesPerBurst;

    bool success = openStreamsLocked();
    mShouldBeStreaming.store(success, std::memory_order_release);
    return success;
}

bool HarkAudioEngine::openStreamsLocked() {
    closeStreams();

    mResampleAccumulator = 0;

    // Reset processing states
    mEnvelope = 0.0f;
    mPrevInput = 0.0f;
    mPrevOutput = 0.0f;
    mIsBuffering = true;

    uint32_t fifoCapacity = std::max(static_cast<uint32_t>(mFramesPerBurst) * 16, static_cast<uint32_t>(4096));
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
            ->setSampleRate(mSampleRate)
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
            ->setSampleRate(mSampleRate)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    result = outBuilder.openStream(mOutStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening output stream: %s", oboe::convertToText(result));
        closeStreams();
        return false;
    }

    mOutStream->setBufferSizeInFrames(mFramesPerBurst * 2);
    mInStream->requestStart();
    mOutStream->requestStart();

    return true;
}

void HarkAudioEngine::stop() {
    mShouldBeStreaming.store(false, std::memory_order_release);
    joinRestartThread();
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();
}

void HarkAudioEngine::joinRestartThread() {
    std::lock_guard<std::mutex> lock(mRestartThreadLock);
    if (mRestartThread.joinable()) {
        mRestartThread.join();
    }
}

void HarkAudioEngine::closeStreams() {
    if (mOutStream) { mOutStream->stop(); mOutStream->close(); mOutStream.reset(); }
    if (mInStream) { mInStream->stop(); mInStream->close(); mInStream.reset(); }
    mTranscriptionFifo.reset();
    mFifoBuffer.reset();
    mTranscriptionLevel.store(0.0f, std::memory_order_release);
}

int32_t HarkAudioEngine::readTranscriptionData(float *target, int32_t numFrames) {
    // Called from a JVM thread; the restart thread may swap the FIFO under us
    std::lock_guard<std::mutex> lock(mStreamLock);
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
        int32_t framesToProcess = std::min(numFrames, kMaxFrames);
        for (int i = 0; i < framesToProcess; ++i) {
            float sample = inputData[i] * transcriptionGain;
            mGainedBuffer[i] = sample;
            sumSquares += sample * sample;
        }

        // Calculate RMS level for visualization/gating
        float rms = std::sqrt(sumSquares / static_cast<float>(framesToProcess));

        // Exponential moving average for smoothing the level
        float alpha = 0.1f;
        float currentLevel = mTranscriptionLevel.load(std::memory_order_acquire);
        mTranscriptionLevel.store(currentLevel * (1.0f - alpha) + rms * alpha, std::memory_order_release);

        pushToTranscriptionFifo(mGainedBuffer, framesToProcess);
    } else {
        auto *outputData = static_cast<float *>(audioData);

        if (mIsTranscriptModeEnabled.load(std::memory_order_acquire)) {
            std::fill_n(outputData, numFrames, 0.0f);
            return oboe::DataCallbackResult::Continue;
        }

        // Pre-buffering logic to prevent snapping from minor clock drift/jitter
        uint32_t framesAvailable = mFifoBuffer->getFullFramesAvailable();
        if (mIsBuffering) {
            if (framesAvailable >= static_cast<uint32_t>(numFrames * 4)) {
                mIsBuffering = false;
            } else {
                std::fill_n(outputData, numFrames, 0.0f);
                return oboe::DataCallbackResult::Continue;
            }
        }

        int32_t framesRead = mFifoBuffer->read(outputData, numFrames);

        // Cache atomic loads for the duration of this callback
        const float currentGain = mGain.load(std::memory_order_acquire);
        const float currentAmbientGain = mAmbientGain.load(std::memory_order_acquire);
        const bool dynamicsEnabled = mIsDynamicsProcessingEnabled.load(std::memory_order_acquire);
        const bool nsEnabled = mIsNoiseSuppressionEnabled.load(std::memory_order_acquire);

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

                // 4. SAFETY: Dynamics Processing
                if (dynamicsEnabled) {
                    outputData[i] = applySoftKneeLimiter(mixedSignal);
                } else {
                    outputData[i] = std::clamp(mixedSignal, -1.1f, 1.1f);
                }
            }

            if (framesRead < numFrames) {
                // We ran out of frames! Enter buffering mode to avoid repeated snaps
                std::fill_n(outputData + framesRead, numFrames - framesRead, 0.0f);
                mIsBuffering = true;
            }
        } else {
            std::fill_n(outputData, numFrames, 0.0f);
            mIsBuffering = true;
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
            if (resampledCount >= kMaxFrames) break;
        }
        mResampleAccumulator += skip;
    }
    mResampleAccumulator -= (double)numFrames;

    if (resampledCount > 0) {
        int32_t written = mTranscriptionFifo->write(mResampleBuffer, resampledCount);
        if (written < resampledCount) {
            // FIFO overflow - clear it to avoid stale data
            // Reading in larger chunks is more efficient than sample-by-sample
            float dump[256];
            while(mTranscriptionFifo->read(dump, 256) > 0);
        }
    }
}


float HarkAudioEngine::applySpeechEnhancement(float input) {
    // Simple DC-offset / High-pass filter to reduce low-end rumble
    float output = input - mPrevInput + 0.95f * mPrevOutput;
    mPrevInput = input;
    mPrevOutput = output;
    return output;
}

float HarkAudioEngine::applySoftKneeLimiter(float input) {
    const float threshold = 0.75f; // Slightly lower for safety
    const float kneeWidth = 0.15f;

    // Attack must be fast to catch peaks. Release must be slow to prevent "hissing/pumping" distortion.
    // At 48kHz, 0.05 is ~0.4ms attack. 0.0005 is ~40ms release.
    const float attack = 0.05f;
    const float release = 0.0005f;

    float absInput = std::abs(input);

    // Envelope follower
    if (absInput > mEnvelope) {
        mEnvelope = absInput * attack + mEnvelope * (1.0f - attack);
    } else {
        mEnvelope = absInput * release + mEnvelope * (1.0f - release);
    }

    if (mEnvelope <= threshold - kneeWidth / 2.0f) return input;

    // Proper gain reduction instead of hard-clipping the waveform
    float targetGain = 1.0f;
    if (mEnvelope >= threshold + kneeWidth / 2.0f) {
        targetGain = threshold / mEnvelope;
    } else {
        // Soft knee region
        float diff = mEnvelope - (threshold - kneeWidth / 2.0f);
        float reduction = (diff * diff) / (2.0f * kneeWidth);
        targetGain = (mEnvelope - reduction) / mEnvelope;
    }

    return input * targetGain;
}

void HarkAudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Stream error: %s", oboe::convertToText(error));

    // Exclusive low-latency streams are torn down by the OS on route changes and
    // screen-off. Oboe requires reopening the stream after ErrorDisconnected.
    if (error != oboe::Result::ErrorDisconnected) return;
    if (!mShouldBeStreaming.load(std::memory_order_acquire)) return;

    bool expected = false;
    if (!mRestartPending.compare_exchange_strong(expected, true)) return;

    std::lock_guard<std::mutex> lock(mRestartThreadLock);
    if (mRestartThread.joinable()) {
        mRestartThread.join();
    }
    mRestartThread = std::thread([this] { restartStreams(); });
}

void HarkAudioEngine::restartStreams() {
    for (int attempt = 0; attempt < 3; ++attempt) {
        // Give the new audio route time to settle before reopening
        std::this_thread::sleep_for(std::chrono::milliseconds(250 << attempt));
        if (!mShouldBeStreaming.load(std::memory_order_acquire)) break;

        std::lock_guard<std::mutex> lock(mStreamLock);
        if (!mShouldBeStreaming.load(std::memory_order_acquire)) break;

        if (openStreamsLocked()) {
            __android_log_print(ANDROID_LOG_INFO, TAG,
                    "Streams restarted after disconnect (attempt %d)", attempt + 1);
            mRestartPending.store(false, std::memory_order_release);
            return;
        }
        __android_log_print(ANDROID_LOG_WARN, TAG,
                "Stream restart attempt %d failed", attempt + 1);
    }
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Giving up on stream restart");
    mRestartPending.store(false, std::memory_order_release);
}
