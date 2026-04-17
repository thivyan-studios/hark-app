#ifndef HARK_AUDIO_ENGINE_H
#define HARK_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <mutex>
#include <atomic>
#include <memory>
#include <cstdint>

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

    void setMicrophoneGain(float gain);
    void setAmbientGain(float gain);
    void setNoiseSuppressionEnabled(bool enabled);
    void setDynamicsProcessingEnabled(bool enabled);
    void setTranscriptModeEnabled(bool enabled);

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
    std::atomic<float> mAmbientGain{0.0f};
    std::atomic<bool> mIsNoiseSuppressionEnabled{false};
    std::atomic<bool> mIsDynamicsProcessingEnabled{false};
    std::atomic<bool> mIsTranscriptModeEnabled{false};

    // Soft-knee compressor state (only accessed on audio thread)
    float mEnvelope = 0.0f;

    std::mutex mStreamLock;

    void closeStreams();
    float applySpeechEnhancement(float input);
    float applySoftKneeLimiter(float input);

    // Resampling for transcription
    void pushToTranscriptionFifo(const float* data, int32_t numFrames);
    int32_t mSampleRate = 48000;
    double mResampleAccumulator = 0;
    float mResampleBuffer[2048]{}; // Pre-allocated buffer for resampling
};

#endif //HARK_AUDIO_ENGINE_H
