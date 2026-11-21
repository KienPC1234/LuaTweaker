package com.luatweaker.dimension;

import com.luatweaker.noise.NoiseServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Native evaluator for a {@link TerrainSpec}.
 *
 * <p>No Lua runs here: heights come from weighted noise layers, optionally mapped through a
 * spline, and block choice comes from ordered surface rules. The sampler is immutable and
 * thread-safe, so chunk-generation workers can share it.</p>
 */
public final class TerrainSampler {

    /** Chunk edge in blocks; the height/slope arrays a chunk fills have {@code 16 * 16} entries. */
    public static final int CHUNK_SIZE = 16;
    /** Height grid used for slopes: the chunk plus a one-block border on every side. */
    private static final int SLOPE_GRID = CHUNK_SIZE + 2;

    private final TerrainSpec spec;
    private final NoiseServiceImpl.NoiseSampler noise;
    private final DimensionConfig config;

    public TerrainSampler(@NotNull TerrainSpec spec,
                          @NotNull NoiseServiceImpl.NoiseSampler noise,
                          @NotNull DimensionConfig config) {
        this.spec = spec;
        this.noise = noise;
        this.config = config;
    }

    /** Sea level used for water fill: the spec override, else the dimension config. */
    public int baseHeight() {
        return spec.seaLevel() != null ? spec.seaLevel() : config.seaLevel();
    }

    /** Raw (unclamped) absolute surface Y for one column. */
    public int heightAt(int x, int z) {
        double sum = 0.0;
        double weight = 0.0;
        for (TerrainSpec.WeightedLayer layer : spec.heightLayers()) {
            sum += layer.amplitude() * noise.sample2D(layer.noise(), x, z);
            weight += Math.abs(layer.amplitude());
        }
        double surface = spec.spline().isEmpty()
                ? baseHeight() + sum
                : Splines.evaluate(spec.spline(), weight > 0.0 ? sum / weight : 0.0);
        int height = (int) Math.floor(surface);
        for (TerrainSpec.Platform platform : spec.platforms()) {
            long dx = (long) x - platform.x();
            long dz = (long) z - platform.z();
            if (dx * dx + dz * dz <= (long) platform.radius() * platform.radius() && height < platform.topY()) {
                height = platform.topY();
            }
        }
        return height;
    }

    /** True when the spec defines a 3D density field (overhangs, caves, floating islands). */
    public boolean hasDensity() {
        return spec.density() != null;
    }

    /** False when the dimension must not fill water up to sea level (void/sky dimensions). */
    public boolean waterEnabled() {
        return spec.water();
    }

    /**
     * Samples the density field of one chunk on its coarse cell grid.
     *
     * <p>Cost: {@code cellsX * cellsX * cellsY} noise samples, e.g. 5 x 5 x 81 = 2025 for a
     * 4-block cell in a 384-high dimension.</p>
     *
     * @throws IllegalStateException when the spec has no {@code density} section
     */
    @NotNull
    public DensityField sampleDensity(int chunkX, int chunkZ, int minY, int maxY) {
        TerrainSpec.Density density = spec.density();
        if (density == null) {
            throw new IllegalStateException("terrain spec has no density section");
        }
        if (minY >= maxY) {
            throw new IllegalArgumentException("density range is empty: minY=" + minY + ", maxY=" + maxY);
        }
        int cellSize = density.cellSize();
        int cellHeight = density.cellHeight();
        int cellsX = (CHUNK_SIZE + cellSize - 1) / cellSize + 1;
        int cellsY = (maxY - minY + cellHeight - 1) / cellHeight + 1;
        int baseX = chunkX * CHUNK_SIZE;
        int baseZ = chunkZ * CHUNK_SIZE;

        double[] values = new double[cellsY * cellsX * cellsX];
        int index = 0;
        for (int gy = 0; gy < cellsY; gy++) {
            int sampleY = minY + gy * cellHeight;
            for (int gz = 0; gz < cellsX; gz++) {
                int sampleZ = baseZ + gz * cellSize;
                for (int gx = 0; gx < cellsX; gx++) {
                    double value = 0.0;
                    for (TerrainSpec.WeightedLayer layer : density.layers()) {
                        value += layer.amplitude()
                                * noise.sample3D(layer.noise(), baseX + gx * cellSize, sampleY, sampleZ);
                    }
                    values[index++] = value;
                }
            }
        }

        double[] thresholdByY = computeThresholds(density, minY, maxY);
        return new DensityField(values,
                new DensityField.Grid(cellsX, cellsY, cellSize, cellHeight, baseX, baseZ),
                minY, maxY, thresholdByY, spec.platforms());
    }

    private static double[] computeThresholds(@NotNull TerrainSpec.Density density, int minY, int maxY) {
        double[] thresholdByY = new double[maxY - minY];
        for (int i = 0; i < thresholdByY.length; i++) {
            thresholdByY[i] = density.threshold()
                    + Splines.evaluate(density.thresholdSpline(), minY + i);
        }
        return thresholdByY;
    }

    /**
     * Fills the 256 absolute heights and 256 slopes of one chunk.
     *
     * <p>Slopes are derived from an 18 x 18 height grid so columns on the chunk border use their
     * real neighbours instead of a flat edge.</p>
     */
    public void sampleChunk(int chunkX, int chunkZ, @NotNull int[] heights, @NotNull float... slopes) {
        if (heights.length != CHUNK_SIZE * CHUNK_SIZE || slopes.length != CHUNK_SIZE * CHUNK_SIZE) {
            throw new IllegalArgumentException("height and slope arrays must have "
                    + (CHUNK_SIZE * CHUNK_SIZE) + " entries");
        }
        int baseX = chunkX * CHUNK_SIZE;
        int baseZ = chunkZ * CHUNK_SIZE;
        int[] grid = new int[SLOPE_GRID * SLOPE_GRID];
        for (int gridZ = 0; gridZ < SLOPE_GRID; gridZ++) {
            for (int gridX = 0; gridX < SLOPE_GRID; gridX++) {
                grid[gridZ * SLOPE_GRID + gridX] = heightAt(baseX + gridX - 1, baseZ + gridZ - 1);
            }
        }
        for (int z = 0; z < CHUNK_SIZE; z++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                int index = z * CHUNK_SIZE + x;
                int gridIndex = (z + 1) * SLOPE_GRID + x + 1;
                heights[index] = grid[gridIndex];
                double slopeX = (grid[gridIndex + 1] - grid[gridIndex - 1]) * 0.5;
                double slopeZ = (grid[gridIndex + SLOPE_GRID] - grid[gridIndex - SLOPE_GRID]) * 0.5;
                slopes[index] = (float) Math.max(Math.abs(slopeX), Math.abs(slopeZ));
            }
        }
    }

    /**
     * Returns the block for a depth below the column top, or null when no surface rule matches
     * (the caller then falls back to the config surface/subsurface/filler stack).
     *
     * @param depth {@code topY - y}, 0 for the top block
     * @param y     absolute Y of the block
     * @param slope local column slope in blocks per block
     */
    @Nullable
    public String surfaceBlockAt(int depth, int y, double slope) {
        return spec.surfaceRules().stream()
                .filter(rule -> matchesSurfaceRule(rule, depth, y, slope))
                .map(TerrainSpec.SurfaceRule::block)
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesSurfaceRule(@NotNull TerrainSpec.SurfaceRule rule,
            int depth, int y, double slope) {
        if (depth < 0 || depth > rule.maxDepth()) {
            return false;
        }
        if (y < rule.minY() || y > rule.maxY()) {
            return false;
        }
        return slope >= rule.minSlope();
    }
}