package com.luatweaker.dimension;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Measures the Lua-side terrain and biome pipeline of one dimension without generating
 * real chunks (no block writes, no Minecraft access).
 *
 * <p>The numbers are a developer tool, not a world-generation benchmark: they show how many
 * Lua calls per chunk a dimension costs and how long those calls take. Block placement and
 * lighting are not included.</p>
 */
public final class DimensionBenchmark {

    /** Upper bound on the radius: r = 8 is 289 chunks, enough to stabilize the average. */
    public static final int MAX_RADIUS = 8;
    public static final int DEFAULT_RADIUS = 2;

    private DimensionBenchmark() {}

    /** Result of one benchmark run. */
    public record Report(@NotNull String dimensionId,
                         @NotNull DimensionServiceImpl.TerrainMode mode,
                         int chunkRadius,
                         int chunks,
                         long elapsedNanos,
                         long luaCalls) {

        /** Average wall-clock milliseconds per chunk. */
        public double millisPerChunk() {
            return elapsedNanos / 1_000_000.0 / Math.max(1, chunks);
        }

        /** Average Lua calls per chunk (terrain generator, pickers and biome source). */
        public double luaCallsPerChunk() {
            return (double) luaCalls / Math.max(1, chunks);
        }

        /** Total wall-clock milliseconds for the whole run. */
        public double totalMillis() {
            return elapsedNanos / 1_000_000.0;
        }
    }

    /**
     * Runs the benchmark over a square of chunks centered on (0, 0).
     *
     * @throws IllegalArgumentException for an unknown dimension or a radius outside
     *                                  {@code 0..MAX_RADIUS}
     */
    @NotNull
    public static Report run(@NotNull DimensionServiceImpl service, @NotNull String dimensionId, int radius) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("radius must be between 0 and " + MAX_RADIUS + ", got: " + radius);
        }
        DimensionConfig config = service.getConfig(dimensionId);
        if (config == null) {
            throw new IllegalArgumentException("Unknown dimension '" + dimensionId + "'");
        }
        DimensionServiceImpl.TerrainMode mode = service.getTerrainMode(dimensionId);
        TerrainSampler specSampler = samplerForBenchmark(service, dimensionId, mode);
        int side = 2 * radius + 1;
        int chunks = side * side;
        long luaCalls = 0;
        long start = System.nanoTime();
        for (int chunkX = -radius; chunkX <= radius; chunkX++) {
            for (int chunkZ = -radius; chunkZ <= radius; chunkZ++) {
                luaCalls += sampleChunkCalls(service, config, dimensionId, chunkX, chunkZ, mode, specSampler);
            }
        }
        long elapsed = System.nanoTime() - start;
        return new Report(dimensionId, mode, radius, chunks, elapsed, luaCalls);
    }

    @Nullable
    private static TerrainSampler samplerForBenchmark(@NotNull DimensionServiceImpl service,
            @NotNull String dimensionId, @NotNull DimensionServiceImpl.TerrainMode mode) {
        if (mode != DimensionServiceImpl.TerrainMode.SPEC) {
            return null;
        }
        TerrainSampler sampler = service.createTerrainSampler(dimensionId);
        if (sampler == null) {
            throw new IllegalStateException("Dimension '" + dimensionId
                    + "' is in spec mode but has no terrain spec registered");
        }
        return sampler;
    }

    private static long sampleChunkCalls(@NotNull DimensionServiceImpl service, @NotNull DimensionConfig config,
            @NotNull String dimensionId, int chunkX, int chunkZ,
            @NotNull DimensionServiceImpl.TerrainMode mode, @Nullable TerrainSampler specSampler) {
        switch (mode) {
            case CHUNK -> {
                service.computeChunkTerrain(dimensionId, chunkX, chunkZ);
                return 1 + sampleBiomesBatch(service, dimensionId, chunkX, chunkZ);
            }
            case SPEC -> {
                sampleSpecChunk(specSampler, config, chunkX, chunkZ);
                return sampleBiomesBatch(service, dimensionId, chunkX, chunkZ);
            }
            default -> {
                return sampleLegacyChunk(service, config, dimensionId, chunkX, chunkZ);
            }
        }
    }

    /** Replays the per-column pipeline (terrain generator + pickers) and counts real Lua calls. */
    private static long sampleLegacyChunk(DimensionServiceImpl service, DimensionConfig config,
                                          String dimensionId, int chunkX, int chunkZ) {
        boolean hasGenerator = service.getTerrainGenerator(dimensionId) != null;
        boolean hasBlockPicker = service.getBlockPicker(dimensionId) != null;
        boolean hasStrataPicker = service.getStrataPicker(dimensionId) != null;
        boolean hasBiomeProvider = service.getBiomeProvider(dimensionId) != null;
        long calls = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                calls += sampleLegacyColumn(service, dimensionId, hasGenerator, hasBlockPicker,
                        hasStrataPicker, chunkX * 16 + x, chunkZ * 16 + z, config);
            }
        }
        if (hasBiomeProvider) {
            // The biome source asks once per quart: 4 x 4 quarts per chunk.
            for (int quartZ = 0; quartZ < 4; quartZ++) {
                for (int quartX = 0; quartX < 4; quartX++) {
                    service.computeBiomeId(dimensionId, chunkX * 16 + quartX * 4, chunkZ * 16 + quartZ * 4);
                    calls++;
                }
            }
        }
        return calls;
    }

    private static long sampleLegacyColumn(@NotNull DimensionServiceImpl service,
            @NotNull String dimensionId, boolean hasGenerator, boolean hasBlockPicker,
            boolean hasStrataPicker, int worldX, int worldZ, @NotNull DimensionConfig config) {
        long calls = 0;
        DimensionServiceImpl.SurfaceResult surface =
                service.computeSurface(dimensionId, worldX, worldZ, config.seaLevel());
        if (hasGenerator) {
            calls++;
        }
        if (hasBlockPicker) {
            service.computeBlockOverrides(dimensionId, worldX, worldZ, surface.height(),
                    config.minHeight(), config.maxHeight());
            calls++;
        }
        if (hasStrataPicker) {
            service.computeStrataOverrides(dimensionId, worldX, worldZ, surface.height());
            calls++;
        }
        return calls;
    }

    /** Replays the native spec pipeline: heights or density, plus one surface lookup per column. */
    private static void sampleSpecChunk(TerrainSampler sampler, DimensionConfig config,
                                        int chunkX, int chunkZ) {
        int columns = TerrainSampler.CHUNK_SIZE * TerrainSampler.CHUNK_SIZE;
        if (sampler.hasDensity()) {
            DensityField density = sampler.sampleDensity(chunkX, chunkZ,
                    config.minHeight(), config.maxHeight());
            for (int z = 0; z < TerrainSampler.CHUNK_SIZE; z++) {
                for (int x = 0; x < TerrainSampler.CHUNK_SIZE; x++) {
                    sampler.surfaceBlockAt(0, density.topSolid(x, z), 0.0);
                }
            }
            return;
        }
        int[] heights = new int[columns];
        float[] slopes = new float[columns];
        sampler.sampleChunk(chunkX, chunkZ, heights, slopes);
        for (int i = 0; i < columns; i++) {
            sampler.surfaceBlockAt(0, heights[i], slopes[i]);
        }
    }

    /** Runs the batch biome provider for this chunk and returns 1 when it exists, else 0. */
    private static long sampleBiomesBatch(DimensionServiceImpl service, String dimensionId,
                                          int chunkX, int chunkZ) {
        if (!service.hasBiomeBatch(dimensionId)) {
            return 0;
        }
        service.computeBiomeBatch(dimensionId, chunkX, chunkZ);
        return 1;
    }
}
