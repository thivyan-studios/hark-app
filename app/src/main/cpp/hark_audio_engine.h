#ifndef HARK_AUDIO_ENGINE_H
#define HARK_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <mutex>
#include <atomic>
#include <memory>
#include <cstdint>
#include <vector>
#include <thread>

namespace oboe {
    class FifoBuffer;
}

class HarkAudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    HarkAudioEngine();
    ~HarkAudioEngine() override;

    bool start(int32_t sampleRate, int32_t framesPerBurst);
    void stop();

    int32_t readTranscriptionData(float *target, int32_t numFrames);
    float getTranscriptionLevel();
    int32_t getSessionId();

    void setMicrophoneGain(float gain);
    void setNoiseSuppressionEnabled(bool enabled);
    void setDynamicsProcessingEnabled(bool enabled);
    void setTranscriptModeEnabled(bool enabled);
    void setTrebleBoostEnabled(bool enabled);

    // From oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;

    // From oboe::AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mInStream;
    std::shared_ptr<oboe::AudioStream> mOutStream;

    std::unique_ptr<oboe::FifoBuffer> mFifoBuffer;
    std::unique_ptr<oboe::FifoBuffer> mTranscriptionFifo;

    // Use atomic for thread-safe access from audio thread without locking
    std::atomic<float> mGain{1.0f};
    std::atomic<bool> mIsNoiseSuppressionEnabled{false};
    std::atomic<bool> mIsDynamicsProcessingEnabled{false};
    std::atomic<bool> mIsTranscriptModeEnabled{false};
    std::atomic<bool> mIsTrebleBoostEnabled{false};

    // Live audio level for transcription gating/visuals
    std::atomic<float> mTranscriptionLevel{0.0f};

    // Processing state (only accessed on audio thread)
    float mEnvelope = 0.0f;
    float mPrevInput = 0.0f;
    float mPrevOutput = 0.0f;

    // Treble boost filter state (Biquad)
    float mX1 = 0, mX2 = 0, mY1 = 0, mY2 = 0;
    float mB0 = 1, mB1 = 0, mB2 = 0, mA1 = 0, mA2 = 0;

    bool mIsBuffering = true;

    std::mutex mStreamLock;

    // Stream restart on disconnect (screen-off / route changes tear down
    // exclusive low-latency streams; they must be reopened, see onErrorAfterClose)
    std::atomic<bool> mShouldBeStreaming{false};
    std::atomic<bool> mRestartPending{false};
    std::thread mRestartThread;
    std::mutex mRestartThreadLock;
    int32_t mFramesPerBurst = 192;

    bool openStreamsLocked();
    void restartStreams();
    void joinRestartThread();

    void updateTrebleBoostCoefficients(int32_t sampleRate);
    void closeStreams();
    float applyTrebleBoost(float input);
    float applySoftKneeLimiter(float input);

    // Resampling for transcription
    void pushToTranscriptionFifo(const float* data, int32_t numFrames);
    int32_t mSampleRate = 48000;
    double mResampleAccumulator = 0;

    // Pre-allocated buffers for audio processing to avoid heap allocation in callback
    static constexpr int32_t kMaxFrames = 2048;
    float mResampleBuffer[kMaxFrames]{};
    float mGainedBuffer[kMaxFrames]{};
};

#endif //HARK_AUDIO_ENGINE_H
