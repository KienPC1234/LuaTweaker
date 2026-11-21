package com.luatweaker.noise;

import com.luatweaker.api.noise.INoiseService;
import org.jetbrains.annotations.NotNull;

/**
 * Seedable, deterministic noise service.
 *
 * <p>All internal generators are immutable; {@code setSeed} swaps a single
 * atomic snapshot, so concurrent chunk-generation threads read a
 * consistent state without locks.</p>
 */
public class NoiseServiceImpl extends AbstractNoiseGridService implements INoiseService {

    private static final String PARAM_LACUNARITY = "lacunarity";
    private static final String PARAM_GAIN = "gain";
    private static final String PARAM_FREQUENCY = "frequency";
    private static final String PARAM_STRENGTH = "strength";
    private static final String PARAM_JITTER = "jitter";
    private static final String PARAM_RETURN_TYPE = "returnType";

    private static final long DEFAULT_SEED = 0L;
    private static final double MIN_JITTER = 0.0;
    private static final double MAX_JITTER = 1.0;
    private static final int MIN_RETURN_TYPE = 0;
    private static final int MAX_RETURN_TYPE = 2;

    public NoiseServiceImpl() {
        super(DEFAULT_SEED);
    }

    public NoiseServiceImpl(long seed) {
        super(seed);
    }

    @Override
    public double fBm(double x, double z, int octaves, double lacunarity, double gain) {
        NoiseValidation.validateOctaves(octaves);
        NoiseValidation.validatePositive(PARAM_LACUNARITY, lacunarity);
        NoiseValidation.validatePositive(PARAM_GAIN, gain);
        return state.get().fbm().noise(x, z, octaves, lacunarity, gain);
    }

    @Override
    public double ridged(double x, double z, int octaves, double frequency, double lacunarity) {
        NoiseValidation.validateOctaves(octaves);
        NoiseValidation.validatePositive(PARAM_FREQUENCY, frequency);
        NoiseValidation.validatePositive(PARAM_LACUNARITY, lacunarity);
        return state.get().ridged().noise(x, z, octaves, frequency, lacunarity);
    }

    @Override
    @NotNull
    public double[] domainWarp(double x, double z, double strength, double frequency) {
        if (!Double.isFinite(strength)) {
            throw new IllegalArgumentException(PARAM_STRENGTH + " must be finite, got: " + strength);
        }
        NoiseValidation.validatePositive(PARAM_FREQUENCY, frequency);
        return state.get().domainWarp().warp(x, z, strength, frequency);
    }

    @Override
    public double voronoi(double x, double z, double jitter, int returnType) {
        if (jitter < MIN_JITTER || jitter > MAX_JITTER) {
            throw new IllegalArgumentException(PARAM_JITTER + " must be between " + MIN_JITTER
                    + " and " + MAX_JITTER + ", got: " + jitter);
        }
        if (returnType < MIN_RETURN_TYPE || returnType > MAX_RETURN_TYPE) {
            throw new IllegalArgumentException(PARAM_RETURN_TYPE + " must be " + MIN_RETURN_TYPE
                    + " (F1), 1 (F2) or " + MAX_RETURN_TYPE + " (F2-F1), got: " + returnType);
        }
        return state.get().voronoi().noise(x, z, jitter, returnType);
    }

    @Override
    public double simplex(double x, double z, double frequency) {
        NoiseValidation.validatePositive(PARAM_FREQUENCY, frequency);
        return state.get().simplex().noise(x * frequency, z * frequency);
    }

    @Override
    public double simplex3(double x, double y, double z, double frequency) {
        NoiseValidation.validatePositive(PARAM_FREQUENCY, frequency);
        return state.get().simplex3d().noise(x * frequency, y * frequency, z * frequency);
    }

    @Override
    public double fbm3(double x, double y, double z, int octaves, double lacunarity, double gain) {
        NoiseValidation.validateOctaves(octaves);
        NoiseValidation.validatePositive(PARAM_LACUNARITY, lacunarity);
        NoiseValidation.validatePositive(PARAM_GAIN, gain);
        return state.get().fbm3d().noise(x, y, z, octaves, lacunarity, gain);
    }

    @Override
    public void setSeed(long seed) {
        state.set(NoiseState.create(seed));
    }

    /**
     * Returns an immutable snapshot of the current noise generators for Java-side evaluation.
     * Several samplers may coexist; a later {@link #setSeed(long)} does not change an existing one.
     */
    @NotNull
    public NoiseSampler createSampler() {
        return new NoiseSampler(state.get());
    }

    /**
     * Thread-safe native sampler used by terrain specs. Delegates to the same generators as the
     * Lua API, so a field evaluated natively matches {@code Noise:Fill2D}/{@code Fill3D}.
     */
    public static final class NoiseSampler {

        private final NoiseState state;

        private NoiseSampler(@NotNull NoiseState state) {
            this.state = state;
        }

        public double sample2D(@NotNull NoiseLayer layer, double x, double z) {
            if (!layer.is2D()) {
                throw new IllegalArgumentException("'" + layer.kind()
                        + "' is not a 2D noise kind; expected one of " + NoiseLayer.KINDS_2D);
            }
            return NoiseSampling.sample2D(state, layer, x, z);
        }

        public double sample3D(@NotNull NoiseLayer layer, double x, double y, double z) {
            if (layer.is2D()) {
                throw new IllegalArgumentException("'" + layer.kind()
                        + "' is not a 3D noise kind; expected one of " + NoiseLayer.KINDS_3D);
            }
            return NoiseSampling.sample3D(state, layer, x, y, z);
        }
    }
}
