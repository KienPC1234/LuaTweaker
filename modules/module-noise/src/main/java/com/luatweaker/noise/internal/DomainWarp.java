package com.luatweaker.noise.internal;

/**
 * Domain warping: distorts input coordinates through stacked fBm passes
 * before sampling, producing organic, folded terrain shapes.
 * Returns the warped coordinates as {warpedX, warpedZ}.
 */
public final class DomainWarp {

    private static final int WARP_OCTAVES = 4;
    private static final double WARP_LACUNARITY = 2.0;
    private static final double WARP_GAIN = 0.5;

    private final FBMNoise fbm;

    public DomainWarp(SimplexNoise base) {
        this.fbm = new FBMNoise(base);
    }

    public double[] warp(double x, double z, double strength, double frequency) {
        // First pass: sample two decorrelated fBm fields to displace the input.
        double qx = fbm.noise(x * frequency, z * frequency, WARP_OCTAVES, WARP_LACUNARITY, WARP_GAIN);
        double qz = fbm.noise((x + 5.2) * frequency, (z + 1.3) * frequency, WARP_OCTAVES, WARP_LACUNARITY, WARP_GAIN);

        // Second pass: sample the warped coordinates.
        double rx = fbm.noise(x * frequency + strength * qx, z * frequency + strength * qz,
                WARP_OCTAVES, WARP_LACUNARITY, WARP_GAIN);
        double rz = fbm.noise(x * frequency + strength * qx + 5.2, z * frequency + strength * qz + 1.3,
                WARP_OCTAVES, WARP_LACUNARITY, WARP_GAIN);

        return new double[]{rx, rz};
    }
}
