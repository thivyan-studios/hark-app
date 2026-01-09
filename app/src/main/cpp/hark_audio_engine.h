#ifndef HARK_AUDIO_ENGINE_H
#define HARK_AUDIO_ENGINE_H

#include <oboe/Oboe.h>
#include <mutex>
#include <memory>
#include <cstdint>

class HarkAudioEngine : public oboe::AudioStreamCallback {
public:
    HarkAudioEngine();
    virtual ~HarkAudioEngine();

    bool start();
    void stop();

    void setMicrophoneGain(float gain);
    void setNoiseSuppressionEnabled(bool enabled);
    void setDynamicsProcessingEnabled(bool enabled);

    // From oboe::AudioStreamCallback
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> mInStream;
    std::shared_ptr<oboe::AudioStream> mOutStream;

    float mGain = 1.0f;
    bool mIsNoiseSuppressionEnabled = false;
    bool mIsDynamicsProcessingEnabled = false;

    std::mutex mStreamLock;

    void closeStreams();
};

#endif //HARK_AUDIO_ENGINE_H
