#ifndef BIQUAD_FILTER_H
#define BIQUAD_FILTER_H

#include <atomic>

struct BiquadCoefficients {
    float b0, b1, b2, a1, a2;
};

class BiquadFilter {
public:
    void setCoefficients(const BiquadCoefficients& coeffs) {
        mPendingCoeffs.store(coeffs, std::memory_order_relaxed);
    }

    void updateFromPending() {
        mActiveCoeffs = mPendingCoeffs.load(std::memory_order_relaxed);
    }

    void reset() {
        z1 = 0;
        z2 = 0;
    }

    inline float process(float in) {
        float out = in * mActiveCoeffs.b0 + z1;
        z1 = in * mActiveCoeffs.b1 + z2 - mActiveCoeffs.a1 * out;
        z2 = in * mActiveCoeffs.b2 - mActiveCoeffs.a2 * out;
        return out;
    }

private:
    std::atomic<BiquadCoefficients> mPendingCoeffs{ {1.0f, 0.0f, 0.0f, 0.0f, 0.0f} };
    BiquadCoefficients mActiveCoeffs{ 1.0f, 0.0f, 0.0f, 0.0f, 0.0f };
    float z1{0}, z2{0};
};

#endif // BIQUAD_FILTER_H
