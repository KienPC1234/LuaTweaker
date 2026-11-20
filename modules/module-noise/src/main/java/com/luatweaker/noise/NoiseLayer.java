package com.luatweaker.noise;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Immutable description of one native noise layer.
 *
 * <p>This is the Java-side counterpart of the Lua batch spec used by
 * {@code Noise:Fill2D}/{@code Fill3D}: the same kinds and parameters, but validated once at
 * construction so per-sample evaluation is a pure switch. Terrain specs are built from lists
 * of these layers, which lets the chunk generator evaluate a whole field without entering Lua.</p>
 *
 * <p>{@link #frequency()} scales world coordinates for every kind (one block of frequency 1.0
 * is one noise unit). {@link #warpStrength()} is a world-space displacement in blocks; the
 * sampler converts it to noise units so it does not depend on the layer frequency.</p>
 */
public record NoiseLayer(
        @NotNull String kind,
        double frequency,
        int octaves,
        double lacunarity,
        double gain,
        double jitter,
        int voronoiType,
        double warpStrength,
        double warpFrequency
) {

    public static final Set<String> KINDS_2D = Set.of("fbm", "simplex", "ridged", "voronoi", "warped_fbm");
    public static final Set<String> KINDS_3D = Set.of("fbm3", "simplex3");
    /** Upper bound on octaves: beyond this the sample cost grows with no visible detail gain. */
    public static final int MAX_OCTAVES = 32;

    public NoiseLayer {
        if (kind == null || (!KINDS_2D.contains(kind) && !KINDS_3D.contains(kind))) {
            throw new IllegalArgumentException("unknown noise kind '" + kind + "'; expected one of "
                    + KINDS_2D + " or " + KINDS_3D);
        }
        if (!Double.isFinite(frequency) || frequency <= 0.0) {
            throw new IllegalArgumentException("noise frequency must be a positive finite number, got: " + frequency);
        }
        if (octaves < 1 || octaves > MAX_OCTAVES) {
            throw new IllegalArgumentException("noise octaves must be between 1 and " + MAX_OCTAVES
                    + ", got: " + octaves);
        }
        if (!Double.isFinite(lacunarity) || lacunarity <= 0.0) {
            throw new IllegalArgumentException("noise lacunarity must be a positive finite number, got: " + lacunarity);
        }
        if (!Double.isFinite(gain) || gain <= 0.0) {
            throw new IllegalArgumentException("noise gain must be a positive finite number, got: " + gain);
        }
        if (!Double.isFinite(jitter) || jitter < 0.0 || jitter > 1.0) {
            throw new IllegalArgumentException("noise jitter must be between 0.0 and 1.0, got: " + jitter);
        }
        if (voronoiType < 0 || voronoiType > 2) {
            throw new IllegalArgumentException("noise voronoiType must be 0, 1 or 2, got: " + voronoiType);
        }
        if (!Double.isFinite(warpStrength)) {
            throw new IllegalArgumentException("noise warpStrength must be finite, got: " + warpStrength);
        }
        if (!Double.isFinite(warpFrequency) || warpFrequency <= 0.0) {
            throw new IllegalArgumentException("noise warpFrequency must be a positive finite number, got: "
                    + warpFrequency);
        }
    }

    /** True when this kind can be evaluated by the 2D sampler. */
    public boolean is2D() {
        return KINDS_2D.contains(kind);
    }
}
