#ifndef NOISE_GATE_H
#define NOISE_GATE_H

#include <algorithm>
#include <cmath>

class NoiseGate {
public:
    void reset() {
        mEnvelope = 0.0f;
        mNoiseFloor = 0.01f;
    }

    inline float process(float in) {
        float absSample = std::abs(in);
        
        if (absSample > mEnvelope) 
            mEnvelope += kAttackAlpha * (absSample - mEnvelope);
        else 
            mEnvelope += kReleaseAlpha * (absSample - mEnvelope);
        
        if (mEnvelope < mNoiseFloor) 
            mNoiseFloor += kNoiseAlpha * (mEnvelope - mNoiseFloor);
        else 
            mNoiseFloor += kNoiseAlpha * 0.1f * (mEnvelope - mNoiseFloor);

        float threshold = mNoiseFloor * 3.0f;
        if (mEnvelope < threshold) {
            float attenuation = std::pow(mEnvelope / (threshold + 1e-6f), kExpanderRatio - 1.0f);
            return in * std::max(0.1f, attenuation);
        }
        return in;
    }

private:
    float mEnvelope = 0.0f;
    float mNoiseFloor = 0.01f;
    
    static constexpr float kAttackAlpha = 0.1f;
    static constexpr float kReleaseAlpha = 0.001f;
    static constexpr float kNoiseAlpha = 0.0001f;
    static constexpr float kExpanderRatio = 2.0f;
};

#endif // NOISE_GATE_H
