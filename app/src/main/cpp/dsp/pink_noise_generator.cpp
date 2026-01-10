#include "pink_noise_generator.h"

PinkNoiseGenerator::PinkNoiseGenerator() : distribution(-1.0f, 1.0f) {
    reset();
}

void PinkNoiseGenerator::reset() {
    b0 = b1 = b2 = b3 = b4 = b5 = b6 = 0.0f;
}

float PinkNoiseGenerator::generate() {
    float white = distribution(generator);
    
    b0 = 0.99886f * b0 + white * 0.0555179f;
    b1 = 0.99332f * b1 + white * 0.0750759f;
    b2 = 0.96900f * b2 + white * 0.1538520f;
    b3 = 0.86650f * b3 + white * 0.3104856f;
    b4 = 0.55000f * b4 + white * 0.5329522f;
    b5 = -0.7616f * b5 - white * 0.0168980f;
    float pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f;
    b6 = white * 0.115926f;
    
    return pink * PINK_NOISE_GAIN;
}

void PinkNoiseGenerator::fillBuffer(float* buffer, int numFrames) {
    for (int i = 0; i < numFrames; i++) {
        buffer[i] = generate();
    }
}
