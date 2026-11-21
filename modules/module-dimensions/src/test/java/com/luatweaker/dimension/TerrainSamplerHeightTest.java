package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies height sampling: amplitude bounds, splines, determinism and chunk layout. */
class TerrainSamplerHeightTest extends TerrainSamplerTestBase {

    private static final String FBM = "fbm";

    @Test
    void heightStaysWithinSeaLevelPlusMinusAmplitude() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer("simplex", 10.0, 0.01)), List.of(), List.of()), 7L);

        for (int x = -32; x <= 32; x += 8) {
            int height = sampler.heightAt(x, x * 2);
            assertTrue(height >= 53 && height <= 73,
                    "height must stay within seaLevel +/- amplitude, got " + height);
        }
    }

    @Test
    void baseHeightDefaultsToSeaLevel() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer("simplex", 10.0, 0.01)), List.of(), List.of()), 7L);

        assertEquals(63, sampler.baseHeight(), "base height must default to sea level 63");
    }

    @Test
    void zeroAmplitudeIsFlat() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer(FBM, 0.0, 0.01)), List.of(), List.of()), 7L);

        for (int[] column : new int[][] {{0, 0}, {1234, -5678}}) {
            assertEquals(63, sampler.heightAt(column[0], column[1]),
                    "a zero-amplitude layer must produce sea level at " + column[0] + "," + column[1]);
        }
    }

    @Test
    void splineMapsNormalizedSumToMidpoint() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer(FBM, 0.0, 0.01)),
                List.of(new TerrainSpec.SplinePoint(-1.0, 10.0), new TerrainSpec.SplinePoint(1.0, 200.0)),
                List.of()), 7L);

        for (int[] column : new int[][] {{0, 0}, {-900, 900}}) {
            assertEquals(105, sampler.heightAt(column[0], column[1]),
                    "a zero-amplitude sum must map to the spline midpoint at "
                            + column[0] + "," + column[1]);
        }
    }

    @Test
    void sameSeedReproducesHeights() {
        TerrainSpec spec = terrain(null,
                List.of(layer("warped_fbm", 30.0, 0.02), layer("ridged", 50.0, 0.01)),
                List.of(), List.of());
        TerrainSampler first = sampler(spec, 99L);
        TerrainSampler second = sampler(spec, 99L);

        for (int i = 0; i < 64; i++) {
            int x = i * 17;
            int z = i * -23;
            assertEquals(first.heightAt(x, z), second.heightAt(x, z),
                    "the same seed must reproduce heights at " + x + "," + z);
        }
    }

    @Test
    void differentSeedChangesTerrain() {
        TerrainSpec spec = terrain(null,
                List.of(layer("warped_fbm", 30.0, 0.02), layer("ridged", 50.0, 0.01)),
                List.of(), List.of());
        TerrainSampler first = sampler(spec, 99L);
        TerrainSampler differentSeed = sampler(spec, 100L);
        boolean anyDifference = false;
        for (int i = 0; i < 64; i++) {
            int x = i * 17;
            int z = i * -23;
            if (first.heightAt(x, z) != differentSeed.heightAt(x, z)) {
                anyDifference = true;
            }
        }

        assertTrue(anyDifference, "a different seed must change the terrain");
    }

    @Test
    void sampleChunkUsesSpecBaseHeight() {
        TerrainSampler sampler = sampler(terrain(70,
                List.of(layer(FBM, 0.0, 0.01)), List.of(), List.of()), 7L);

        assertEquals(70, sampler.baseHeight(), "a spec sea level override must change the base height");
    }

    @Test
    void sampleChunkFillsFlatHeights() {
        TerrainSampler sampler = sampler(terrain(70,
                List.of(layer(FBM, 0.0, 0.01)), List.of(), List.of()), 7L);
        int[] heights = new int[256];
        float[] slopes = new float[256];
        sampler.sampleChunk(3, -4, heights, slopes);

        for (int i = 0; i < 256; i++) {
            assertEquals(70, heights[i], "a zero-amplitude chunk must be flat at index " + i);
        }
    }

    @Test
    void sampleChunkFillsZeroSlopes() {
        TerrainSampler sampler = sampler(terrain(70,
                List.of(layer(FBM, 0.0, 0.01)), List.of(), List.of()), 7L);
        int[] heights = new int[256];
        float[] slopes = new float[256];
        sampler.sampleChunk(3, -4, heights, slopes);

        for (int i = 0; i < 256; i++) {
            assertEquals(0.0f, slopes[i], 1e-6f, "a flat chunk must have zero slope at index " + i);
        }
    }

    @Test
    void sampleChunkMatchesHeightAtForEachColumn() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer("simplex", 25.0, 0.03)), List.of(), List.of()), 11L);
        int[] heights = new int[256];
        float[] slopes = new float[256];
        sampler.sampleChunk(2, 5, heights, slopes);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(sampler.heightAt(2 * 16 + x, 5 * 16 + z), heights[z * 16 + x],
                        "column height mismatch at " + x + "," + z);
            }
        }
    }
}
