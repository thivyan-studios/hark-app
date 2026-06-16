#include "hark_audio_engine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <thread>
#include <oboe/FifoBuffer.h>

#define TAG "HarkAudioEngine"

HarkAudioEngine::HarkAudioEngine() = default;

HarkAudioEngine::~HarkAudioEngine() {
    stop();
}

bool HarkAudioEngine::start(int32_t sampleRate, int32_t framesPerBurst,
                            int32_t outputDeviceId, int32_t inputDeviceId) {
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();

    mSampleRate          = sampleRate;
    mFramesPerBurst      = framesPerBurst;
    mOutputDeviceId      = outputDeviceId;
    mInputDeviceId       = inputDeviceId;
    mResampleAccumulator = 0;

    mEnvelope   = 0.0f;
    mPrevInput  = 0.0f;
    mPrevOutput = 0.0f;

    mTranscriptionFifo = std::make_unique<oboe::FifoBuffer>(sizeof(float), 160000);

    // LiveEffect pattern: open OUTPUT first, then INPUT with matched sample rate
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sampleRate)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music)
            ->setDataCallback(this)
            ->setErrorCallback(this);
    if (outputDeviceId != oboe::kUnspecified) {
        outBuilder.setDeviceId(outputDeviceId);
    }

    oboe::Result result = outBuilder.openStream(mOutStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening output stream: %s", oboe::convertToText(result));
        return false;
    }

    // Use actual negotiated sample rate so input matches without resampling
    int32_t actualSampleRate = mOutStream->getSampleRate();
    mSampleRate = actualSampleRate;
    mOutStream->setBufferSizeInFrames(framesPerBurst * 2);

    // INPUT: no DataCallback - read directly in output callback
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(actualSampleRate)
            ->setInputPreset(oboe::InputPreset::Unprocessed)
            ->setErrorCallback(this);
    if (inputDeviceId != oboe::kUnspecified) {
        inBuilder.setDeviceId(inputDeviceId);
    }

    result = inBuilder.openStream(mInStream);
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening input stream: %s", oboe::convertToText(result));
        closeStreams();
        return false;
    }

    result = mInStream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting input stream: %s", oboe::convertToText(result));
        closeStreams();
        return false;
    }

    result = mOutStream->requestStart();
    if (result != oboe::Result::OK) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error starting output stream: %s", oboe::convertToText(result));
        closeStreams();
        return false;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG,
        "Audio engine started (sampleRate=%d, burst=%d, outDev=%d, inDev=%d)",
        actualSampleRate, framesPerBurst, outputDeviceId, inputDeviceId);
    return true;
}

void HarkAudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mStreamLock);
    closeStreams();
}

void HarkAudioEngine::closeStreams() {
    if (mOutStream) { mOutStream->stop(); mOutStream->close(); mOutStream.reset(); }
    if (mInStream)  { mInStream->stop();  mInStream->close();  mInStream.reset();  }
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
void HarkAudioEngine::setAmbientGain(float gain)    { mAmbientGain.store(gain, std::memory_order_release); }
void HarkAudioEngine::setNoiseSuppressionEnabled(bool enabled) {
    mIsNoiseSuppressionEnabled.store(enabled, std::memory_order_release);
}
void HarkAudioEngine::setDynamicsProcessingEnabled(bool enabled) {
    mIsDynamicsProcessingEnabled.store(enabled, std::memory_order_release);
}
void HarkAudioEngine::setTranscriptModeEnabled(bool enabled) {
    mIsTranscriptModeEnabled.store(enabled, std::memory_order_release);
}

// LiveEffect pattern: only OUTPUT stream fires this callback.
oboe::DataCallbackResult HarkAudioEngine::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {

    auto *outputData = static_cast<float *>(audioData);

    // Non-blocking read from input stream (timeout=0)
    oboe::ResultWithValue<int32_t> readResult = mInStream->read(mInputBuffer, numFrames, 0);
    int32_t framesRead = readResult ? readResult.value() : 0;

    if (framesRead > 0) {
        int32_t framesToProcess = std::min(framesRead, kMaxFrames);
        float currentGain = mGain.load(std::memory_order_acquire);
        float transcriptionGain = currentGain * 10.0f;
        float rawSumSquares = 0.0f;
        float sumSquares    = 0.0f;

        for (int i = 0; i < framesToProcess; ++i) {
            rawSumSquares += mInputBuffer[i] * mInputBuffer[i];
            float sample     = mInputBuffer[i] * transcriptionGain;
            mGainedBuffer[i] = sample;
            sumSquares      += sample * sample;
        }

        mInputLevel.store(std::sqrt(rawSumSquares / static_cast<float>(framesToProcess)),
                          std::memory_order_release);

        float rms    = std::sqrt(sumSquares / static_cast<float>(framesToProcess));
        float alpha  = 0.1f;
        float curLvl = mTranscriptionLevel.load(std::memory_order_acquire);
        mTranscriptionLevel.store(curLvl * (1.0f - alpha) + rms * alpha, std::memory_order_release);
        pushToTranscriptionFifo(mGainedBuffer, framesToProcess);
    }

    if (mIsTranscriptModeEnabled.load(std::memory_order_acquire)) {
        std::fill_n(outputData, numFrames, 0.0f);
        return oboe::DataCallbackResult::Continue;
    }

    float currentGain        = mGain.load(std::memory_order_acquire);
    float currentAmbientGain = mAmbientGain.load(std::memory_order_acquire);
    bool dynamicsEnabled     = mIsDynamicsProcessingEnabled.load(std::memory_order_acquire);
    bool nsEnabled           = mIsNoiseSuppressionEnabled.load(std::memory_order_acquire);

    for (int i = 0; i < numFrames; i++) {
        float rawSample = (i < framesRead) ? mInputBuffer[i] : 0.0f;
        float processed = nsEnabled ? applySpeechEnhancement(rawSample) : rawSample;
        float mixed     = processed * currentGain + rawSample * currentAmbientGain;
        outputData[i]   = dynamicsEnabled ? applySoftKneeLimiter(mixed) : std::clamp(mixed, -1.1f, 1.1f);
    }

    if (framesRead > 0) {
        float outSumSq = 0.0f;
        for (int i = 0; i < framesRead; ++i) outSumSq += outputData[i] * outputData[i];
        mOutputLevel.store(std::sqrt(outSumSq / static_cast<float>(framesRead)), std::memory_order_release);
    } else {
        mOutputLevel.store(0.0f, std::memory_order_release);
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
            float dump[256];
            while(mTranscriptionFifo->read(dump, 256) > 0);
        }
    }
}

float HarkAudioEngine::applySpeechEnhancement(float input) {
    float output = input - mPrevInput + 0.95f * mPrevOutput;
    mPrevInput  = input;
    mPrevOutput = output;
    return output;
}

float HarkAudioEngine::applySoftKneeLimiter(float input) {
    const float threshold = 0.75f;
    const float kneeWidth = 0.15f;
    const float attack    = 0.05f;
    const float release   = 0.0005f;

    float absInput = std::abs(input);
    if (absInput > mEnvelope) {
        mEnvelope = absInput * attack  + mEnvelope * (1.0f - attack);
    } else {
        mEnvelope = absInput * release + mEnvelope * (1.0f - release);
    }

    if (mEnvelope <= threshold - kneeWidth / 2.0f) return input;

    float targetGain = 1.0f;
    if (mEnvelope >= threshold + kneeWidth / 2.0f) {
        targetGain = threshold / mEnvelope;
    } else {
        float diff      = mEnvelope - (threshold - kneeWidth / 2.0f);
        float reduction = (diff * diff) / (2.0f * kneeWidth);
        targetGain      = (mEnvelope - reduction) / mEnvelope;
    }
    return input * targetGain;
}

void HarkAudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    __android_log_print(ANDROID_LOG_ERROR, TAG, "Stream error: %s", oboe::convertToText(error));
    if (error == oboe::Result::ErrorDisconnected) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "Device disconnected - restarting streams");
        int32_t sr   = mSampleRate;
        int32_t fpb  = mFramesPerBurst;
        int32_t oDev = mOutputDeviceId;
        int32_t iDev = mInputDeviceId;
        std::thread([this, sr, fpb, oDev, iDev]() {
            start(sr, fpb, oDev, iDev);
        }).detach();
    }
}