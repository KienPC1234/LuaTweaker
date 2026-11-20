package com.luatweaker.dimension;

import com.luatweaker.noise.NoiseLayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Declarative terrain definition: a weighted noise stack mapped to surface heights (optionally
 * through a spline), ordered surface rules, and an optional 3D density field for overhangs,
 * caves and floating islands.
 *
 * <p>The chunk generator evaluates a spec natively - no Lua callback runs per column or per
 * chunk. A spec is pure data, so it can be validated once when the Lua script registers it and
 * then shared by every generation thread.</p>
 *
 * <p>Height model: {@code sum = SUM(amplitude * layer(x, z))}. Without a spline the surface is
 * {@code baseY + sum}; with a spline the surface is the spline output for the normalized sum
 * ({@code sum / SUM(|amplitude|)}, range roughly -1..1), which is how vanilla shapes
 * continentalness into terrain.</p>
 *
 * <p>Density model (when {@link #density()} is present): a block is solid when
 * {@code SUM(amplitude * layer3(x, y, z)) > threshold + thresholdSpline(y)}. A threshold
 * spline that rises away from a Y band produces floating islands; raising the threshold
 * produces caves and overhangs.</p>
 */
public record TerrainSpec(
        @Nullable Integer seaLevel,
        @NotNull List<WeightedLayer> heightLayers,
        @NotNull List<SplinePoint> spline,
        @NotNull List<SurfaceRule> surfaceRules,
        @NotNull List<Platform> platforms,
        @Nullable Density density,
        boolean water
) {

    public TerrainSpec {
        if (heightLayers == null || (heightLayers.isEmpty() && density == null)) {
            throw new IllegalArgumentException(
                    "a terrain spec needs at least one height layer or a density section");
        }
        heightLayers = List.copyOf(heightLayers);
        spline = List.copyOf(spline);
        surfaceRules = List.copyOf(surfaceRules);
        platforms = List.copyOf(platforms);
        validateAscending(spline, "spline");
        if (density != null) {
            validateAscending(density.thresholdSpline(), "density.thresholdSpline");
        }
    }

    private static void validateAscending(List<SplinePoint> points, String what) {
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).input() <= points.get(i - 1).input()) {
                throw new IllegalArgumentException(what + " inputs must be strictly increasing; entry " + i
                        + " (" + points.get(i).input() + ") is not greater than entry " + (i - 1)
                        + " (" + points.get(i - 1).input() + ")");
            }
        }
    }

    /** One weighted noise layer: {@code amplitude * noise(...)}. */
    public record WeightedLayer(@NotNull NoiseLayer noise, double amplitude) {
        public WeightedLayer {
            if (!Double.isFinite(amplitude)) {
                throw new IllegalArgumentException("layer amplitude must be finite, got: " + amplitude);
            }
        }
    }

    /** One spline control point; inputs are ascending. */
    public record SplinePoint(double input, double output) {
        public SplinePoint {
            if (!Double.isFinite(input) || !Double.isFinite(output)) {
                throw new IllegalArgumentException("spline points must be finite, got (" + input + ", " + output + ")");
            }
        }
    }

    /**
     * One ordered surface rule. The first matching rule wins; later rules are lower priority.
     *
     * @param block    block id placed for matching depths
     * @param maxDepth deepest offset below the column top this rule covers (0 = top only)
     * @param minY     lowest Y the rule applies to (inclusive)
     * @param maxY     highest Y the rule applies to (inclusive)
     * @param minSlope minimum column slope (blocks per block) for the rule
     */
    public record SurfaceRule(@NotNull String block, int maxDepth, int minY, int maxY, double minSlope) {
        public SurfaceRule {
            if (maxDepth < 0) {
                throw new IllegalArgumentException("surface rule maxDepth must be >= 0, got: " + maxDepth);
            }
            if (minY > maxY) {
                throw new IllegalArgumentException("surface rule minY (" + minY + ") must be <= maxY (" + maxY + ")");
            }
            if (!Double.isFinite(minSlope) || minSlope < 0.0) {
                throw new IllegalArgumentException("surface rule minSlope must be a non-negative finite number, got: "
                        + minSlope);
            }
        }
    }

    /**
     * 3D density field.
     *
     * @param layers          3D noise layers summed into the density
     * @param threshold       base threshold; larger values carve more away
     * @param cellSize        horizontal sample spacing in blocks (1..16)
     * @param cellHeight      vertical sample spacing in blocks (1..16)
     * @param thresholdSpline per-Y threshold offset (input = absolute Y); empty for none
     */
    public record Density(@NotNull List<WeightedLayer> layers,
                          double threshold,
                          int cellSize,
                          int cellHeight,
                          @NotNull List<SplinePoint> thresholdSpline) {
        public Density {
            if (layers == null || layers.isEmpty()) {
                throw new IllegalArgumentException("density needs at least one 3D layer");
            }
            layers = List.copyOf(layers);
            thresholdSpline = List.copyOf(thresholdSpline);
            if (!Double.isFinite(threshold)) {
                throw new IllegalArgumentException("density threshold must be finite, got: " + threshold);
            }
            if (cellSize < 1 || cellSize > 16) {
                throw new IllegalArgumentException("density cellSize must be between 1 and 16, got: " + cellSize);
            }
            if (cellHeight < 1 || cellHeight > 16) {
                throw new IllegalArgumentException("density cellHeight must be between 1 and 16, got: " + cellHeight);
            }
        }
    }

    /**
     * A solid vertical cylinder forced into the field.
     *
     * <p>Used for guaranteed spawn islands and pillars. With a density field, blocks inside the
     * radius between {@code bottomY} and {@code topY} (inclusive) are always solid; in height
     * mode the column is raised to at least {@code topY} (so the platform is the surface).
     * Surface rules still apply to the platform top.</p>
     */
    public record Platform(int x, int z, int radius, int bottomY, int topY) {
        public Platform {
            if (radius < 1 || radius > 512) {
                throw new IllegalArgumentException("platform radius must be between 1 and 512, got: " + radius);
            }
            if (bottomY > topY) {
                throw new IllegalArgumentException("platform bottomY (" + bottomY
                        + ") must be <= topY (" + topY + ")");
            }
        }
    }
}
