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
    /** Sample rate the mic stream is actually delivering (what Sherpa must be told). */
    int32_t getInputSampleRate();
    /**
     * Diagnostics: level stats for the transcription feed since the last call, then reset.
     * out = [rawPeak, rawSumSquares, boostedPeak, boostedSumSquares, samplesOverFullScale, totalSamples]
     */
    void consumeLevelStats(float *out);

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

    // Level diagnostics for the transcription feed (written by the audio thread only)
    std::atomic<float> mStatRawPeak{0.0f};
    std::atomic<float> mStatRawSumSq{0.0f};
    std::atomic<float> mStatBoostPeak{0.0f};
    std::atomic<float> mStatBoostSumSq{0.0f};
    std::atomic<int32_t> mStatOverFullScale{0};
    std::atomic<int32_t> mStatTotal{0};

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

    // Pre-allocated buffers for audio processing to avoid heap allocation in callback
    static constexpr int32_t kMaxFrames = 2048;

    // Automatic level control for the captions copy (audio thread only)
    static constexpr float kAgcTargetRms = 0.056f;       // about -25 dBFS
    static constexpr float kAgcMaxGain = 178.0f;         // at most +45 dB
    static constexpr float kAgcAttackSeconds = 0.05f;    // react fast to louder speech
    static constexpr float kAgcReleaseSeconds = 3.0f;    // relax slowly through pauses
    static constexpr float kAgcGainSmoothSeconds = 0.1f; // avoid audible gain jumps
    float mAgcEnvelope = 0.0005f;                        // starts near quiet speech (-66 dBFS)
    float mAgcGain = 1.0f;
    float mGainedBuffer[kMaxFrames]{};
};

#endif //HARK_AUDIO_ENGINE_H
