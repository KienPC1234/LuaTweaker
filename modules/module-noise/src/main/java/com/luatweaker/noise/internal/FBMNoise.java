package com.luatweaker.noise.internal;

/**
 * Fractional Brownian Motion: sum of simplex octaves with increasing
 * frequency and decreasing amplitude, normalized to [-1, 1].
 */
public final class FBMNoise {

    private final SimplexNoise base;

    public FBMNoise(SimplexNoise base) {
        this.base = base;
    }

    public double noise(double x, double z, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxAmplitude = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += base.noise(x * frequency, z * frequency) * amplitude;
            maxAmplitude += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return maxAmplitude > 0 ? sum / maxAmplitude : 0.0;
    }
}
