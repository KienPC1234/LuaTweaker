package com.luatweaker.noise.internal;

/**
 * Ridged multifractal noise: folds simplex noise at each octave to
 * produce sharp ridge lines, ideal for mountain terrain.
 * Output roughly in [0, ~1].
 */
public final class RidgedNoise {

    private final SimplexNoise base;

    public RidgedNoise(SimplexNoise base) {
        this.base = base;
    }

    public double noise(double x, double z, int octaves, double frequency, double lacunarity) {
        double sum = 0.0;
        double weight = 1.0;
        double maxSum = 0.0;
        double px = x * frequency;
        double pz = z * frequency;
        for (int i = 0; i < octaves; i++) {
            double signal = 1.0 - Math.abs(base.noise(px, pz));
            signal *= signal;
            signal *= weight;
            maxSum += 1.0;
            weight = Math.min(1.0, Math.max(0.0, signal * 2.0));
            sum += signal;
            px *= lacunarity;
            pz *= lacunarity;
        }
        return maxSum > 0 ? sum / maxSum : 0.0;
    }
}
