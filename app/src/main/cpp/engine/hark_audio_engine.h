#ifndef HARK_AUDIO_ENGINE_H
#define HARK_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <mutex>
#include <memory>
#include <cstdint>
#include <atomic>
#include <vector>
#include "../dsp/pink_noise_generator.h"
#include "../dsp/biquad_filter.h"
#include "../dsp/noise_gate.h"

class HarkAudioEngine : public oboe::AudioStreamCallback {
public:
    HarkAudioEngine(int32_t sampleRate, int32_t framesPerBurst);
    ~HarkAudioEngine() override;

    bool start();
    void stop();

    void setMicrophoneGain(float gain);
    void setNoiseSuppressionEnabled(bool enabled);
    void setDynamicsProcessingEnabled(bool enabled);
    void setEqualizerBands(const float* gains, int numBands);
    void setTestMode(bool enabled);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> mInStream;
    std::shared_ptr<oboe::AudioStream> mOutStream;

    int32_t mSampleRate;
    int32_t mFramesPerBurst;

    std::atomic<float> mGain{1.0f};
    std::atomic<bool> mIsNoiseSuppressionEnabled{false};
    std::atomic<bool> mIsDynamicsProcessingEnabled{false};
    std::atomic<bool> mIsTestMode{false};

    static constexpr int kNumEqBands = 5;
    BiquadFilter mEqBands[kNumEqBands];
    NoiseGate mNoiseGate;
    PinkNoiseGenerator mPinkNoiseGenerator;

    float mLastGains[kNumEqBands] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    
    void updateFilters();
    void closeStreams();
    float processSample(float sample);
    void resetDsp();

    std::mutex mStreamLock;
};

#endif //HARK_AUDIO_ENGINE_H
