package com.luatweaker.noise;

import org.jetbrains.annotations.NotNull;

/**
 * Evaluates a validated {@link NoiseLayer} against one {@link NoiseState} snapshot.
 * Stateless: every method is a pure function of its arguments.
 */
final class NoiseSampling {

    private NoiseSampling() {}

    static double sample2D(@NotNull NoiseState s, @NotNull NoiseLayer layer, double x, double z) {
        double sx = x * layer.frequency();
        double sz = z * layer.frequency();
        return switch (layer.kind()) {
            case "simplex" -> s.simplex().noise(sx, sz);
            case "fbm" -> s.fbm().noise(sx, sz, layer.octaves(), layer.lacunarity(), layer.gain());
            // Coordinates are already frequency-scaled, so ridged gets a unit frequency.
            case "ridged" -> s.ridged().noise(sx, sz, layer.octaves(), 1.0, layer.lacunarity());
            case "voronoi" -> s.voronoi().noise(sx, sz, layer.jitter(), layer.voronoiType());
            case "warped_fbm" -> {
                // warpStrength is a world-space displacement in blocks; convert it to noise
                // units, otherwise a value like 40 tears the field into one-block spikes.
                double strength = layer.warpStrength() * layer.frequency();
                double[] warped = s.domainWarp().warp(sx, sz, strength, layer.warpFrequency());
                yield s.fbm().noise(warped[0], warped[1], layer.octaves(), layer.lacunarity(), layer.gain());
            }
            default -> throw new IllegalArgumentException("'" + layer.kind()
                    + "' is not a 2D noise kind; expected one of " + NoiseLayer.KINDS_2D);
        };
    }

    static double sample3D(@NotNull NoiseState s, @NotNull NoiseLayer layer, double x, double y, double z) {
        double sx = x * layer.frequency();
        double sy = y * layer.frequency();
        double sz = z * layer.frequency();
        return switch (layer.kind()) {
            case "simplex3" -> s.simplex3d().noise(sx, sy, sz);
            case "fbm3" -> s.fbm3d().noise(sx, sy, sz, layer.octaves(), layer.lacunarity(), layer.gain());
            default -> throw new IllegalArgumentException("'" + layer.kind()
                    + "' is not a 3D noise kind; expected one of " + NoiseLayer.KINDS_3D);
        };
    }
}
