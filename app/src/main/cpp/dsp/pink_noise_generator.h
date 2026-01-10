#ifndef PINK_NOISE_GENERATOR_H
#define PINK_NOISE_GENERATOR_H

#include <random>

class PinkNoiseGenerator {
public:
    PinkNoiseGenerator();
    void reset();
    float generate();
    void fillBuffer(float* buffer, int numFrames);

private:
    float b0, b1, b2, b3, b4, b5, b6;
    std::default_random_engine generator;
    std::uniform_real_distribution<float> distribution;
    
    static constexpr float PINK_NOISE_GAIN = 0.11f;
};

#endif // PINK_NOISE_GENERATOR_H
