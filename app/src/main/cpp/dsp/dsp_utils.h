#ifndef DSP_UTILS_H
#define DSP_UTILS_H

#include <cmath>
#include "biquad_filter.h"

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

class DspUtils {
public:
    static BiquadCoefficients calculatePeakingEq(float frequency, float sampleRate, float gainDb, float Q = 0.707f) {
        float A = std::pow(10.0f, gainDb / 40.0f);
        float omega = 2.0f * (float)M_PI * frequency / sampleRate;
        float sn = std::sin(omega);
        float cs = std::cos(omega);
        float alpha = sn / (2.0f * Q);

        float a0 = 1.0f + alpha / A;
        BiquadCoefficients coeffs;
        coeffs.b0 = (1.0f + alpha * A) / a0;
        coeffs.b1 = (-2.0f * cs) / a0;
        coeffs.b2 = (1.0f - alpha * A) / a0;
        coeffs.a1 = (-2.0f * cs) / a0;
        coeffs.a2 = (1.0f - alpha / A) / a0;
        return coeffs;
    }
};

#endif // DSP_UTILS_H
