package com.luatweaker.noise;

import com.luatweaker.api.noise.INoiseService;
import com.luatweaker.noise.internal.DomainWarp;
import com.luatweaker.noise.internal.FBMNoise;
import com.luatweaker.noise.internal.RidgedNoise;
import com.luatweaker.noise.internal.SimplexNoise;
import com.luatweaker.noise.internal.VoronoiNoise;
import org.jetbrains.annotations.NotNull;

/**
 * Seedable, deterministic noise service.
 *
 * <p>All internal generators are immutable; {@code setSeed} swaps a single
 * volatile snapshot, so concurrent chunk-generation threads read a
 * consistent state without locks.</p>
 */
public class NoiseServiceImpl implements INoiseService {

    private record NoiseState(
            SimplexNoise simplex,
            FBMNoise fbm,
            RidgedNoise ridged,
            DomainWarp domainWarp,
            VoronoiNoise voronoi
    ) {}

    private volatile NoiseState state;

    public NoiseServiceImpl() {
        setSeed(0L);
    }

    public NoiseServiceImpl(long seed) {
        setSeed(seed);
    }

    @Override
    public double fBm(double x, double z, int octaves, double lacunarity, double gain) {
        validateOctaves(octaves);
        validatePositive("lacunarity", lacunarity);
        validatePositive("gain", gain);
        return state.fbm.noise(x, z, octaves, lacunarity, gain);
    }

    @Override
    public double ridged(double x, double z, int octaves, double frequency, double lacunarity) {
        validateOctaves(octaves);
        validatePositive("frequency", frequency);
        validatePositive("lacunarity", lacunarity);
        return state.ridged.noise(x, z, octaves, frequency, lacunarity);
    }

    @Override
    @NotNull
    public double[] domainWarp(double x, double z, double strength, double frequency) {
        if (!Double.isFinite(strength)) {
            throw new IllegalArgumentException("strength must be finite, got: " + strength);
        }
        validatePositive("frequency", frequency);
        return state.domainWarp.warp(x, z, strength, frequency);
    }

    @Override
    public double voronoi(double x, double z, double jitter, int returnType) {
        if (jitter < 0.0 || jitter > 1.0) {
            throw new IllegalArgumentException("jitter must be between 0.0 and 1.0, got: " + jitter);
        }
        if (returnType < 0 || returnType > 2) {
            throw new IllegalArgumentException("returnType must be 0 (F1), 1 (F2) or 2 (F2-F1), got: " + returnType);
        }
        return state.voronoi.noise(x, z, jitter, returnType);
    }

    @Override
    public double simplex(double x, double z, double frequency) {
        validatePositive("frequency", frequency);
        return state.simplex.noise(x * frequency, z * frequency);
    }

    @Override
    public void setSeed(long seed) {
        SimplexNoise simplex = new SimplexNoise(seed);
        state = new NoiseState(
                simplex,
                new FBMNoise(simplex),
                new RidgedNoise(simplex),
                new DomainWarp(simplex),
                new VoronoiNoise(seed)
        );
    }

    private void validateOctaves(int octaves) {
        if (octaves < 1) {
            throw new IllegalArgumentException("octaves must be >= 1, got: " + octaves);
        }
    }

    private void validatePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be a positive finite number, got: " + value);
        }
    }
}
