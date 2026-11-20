package com.luatweaker.dimension;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.noise.NoiseLayer;
import com.luatweaker.noise.NoiseServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TerrainSamplerTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private DimensionConfig config;

    @BeforeEach
    void setup() {
        config = DimensionConfig.defaults("minecraft:test");
    }

    private TerrainSampler sampler(TerrainSpec spec, long seed) {
        NoiseServiceImpl noise = new NoiseServiceImpl(seed);
        return new TerrainSampler(spec, noise.createSampler(), config);
    }

    private static TerrainSpec.WeightedLayer layer(String kind, double amplitude, double frequency) {
        NoiseLayer noise = new NoiseLayer(kind, frequency, 4, 2.0, 0.5, 0.8, 0, 20.0, 1.0);
        return new TerrainSpec.WeightedLayer(noise, amplitude);
    }

    private static TerrainSpec spec(Integer seaLevel, List<TerrainSpec.WeightedLayer> layers,
                                    List<TerrainSpec.SplinePoint> spline,
                                    List<TerrainSpec.SurfaceRule> rules) {
        return new TerrainSpec(seaLevel, layers, spline, rules, List.of(), null, true);
    }

    private static TerrainSpec densitySpec(int threshold, List<TerrainSpec.SplinePoint> thresholdSpline) {
        TerrainSpec.Density density = new TerrainSpec.Density(
                List.of(layer("fbm3", 0.0, 0.02)), threshold, 4, 4, thresholdSpline);
        return new TerrainSpec(null, List.of(layer("fbm", 0.0, 0.01)), List.of(), List.of(),
                List.of(), density, true);
    }

    @Test
    void heightAt_WithoutSplineAddsLayersToSeaLevel() {
        TerrainSpec spec = spec(null,
                List.of(layer("simplex", 10.0, 0.01)), List.of(), List.of());
        TerrainSampler sampler = sampler(spec, 7L);

        for (int x = -32; x <= 32; x += 8) {
            int height = sampler.heightAt(x, x * 2);
            assertTrue(height >= 53 && height <= 73,
                    "height must stay within seaLevel +/- amplitude, got " + height);
        }
        assertEquals(63, sampler.baseHeight());
    }

    @Test
    void heightAt_ZeroAmplitudeIsFlat() {
        TerrainSpec spec = spec(null,
                List.of(layer("fbm", 0.0, 0.01)), List.of(), List.of());
        TerrainSampler sampler = sampler(spec, 7L);
        assertEquals(63, sampler.heightAt(0, 0));
        assertEquals(63, sampler.heightAt(1234, -5678));
    }

    @Test
    void heightAt_SplineMapsNormalizedSum() {
        TerrainSpec spec = spec(null,
                List.of(layer("fbm", 0.0, 0.01)),
                List.of(new TerrainSpec.SplinePoint(-1.0, 10.0), new TerrainSpec.SplinePoint(1.0, 200.0)),
                List.of());
        TerrainSampler sampler = sampler(spec, 7L);
        // Sum is 0 for a zero-amplitude layer, so the spline is evaluated at 0.0 -> midpoint.
        assertEquals(105, sampler.heightAt(0, 0));
        assertEquals(105, sampler.heightAt(-900, 900));
    }

    @Test
    void heightAt_IsDeterministicForTheSameSeed() {
        TerrainSpec spec = spec(null,
                List.of(layer("warped_fbm", 30.0, 0.02), layer("ridged", 50.0, 0.01)),
                List.of(), List.of());
        TerrainSampler first = sampler(spec, 99L);
        TerrainSampler second = sampler(spec, 99L);
        TerrainSampler differentSeed = sampler(spec, 100L);

        boolean anyDifference = false;
        for (int i = 0; i < 64; i++) {
            int x = i * 17;
            int z = i * -23;
            assertEquals(first.heightAt(x, z), second.heightAt(x, z), "same seed must reproduce heights");
            if (first.heightAt(x, z) != differentSeed.heightAt(x, z)) anyDifference = true;
        }
        assertTrue(anyDifference, "a different seed must change the terrain");
    }

    @Test
    void sampleChunk_FillsFlatHeightsAndZeroSlopes() {
        TerrainSpec spec = spec(70,
                List.of(layer("fbm", 0.0, 0.01)), List.of(), List.of());
        TerrainSampler sampler = sampler(spec, 7L);
        int[] heights = new int[256];
        float[] slopes = new float[256];

        sampler.sampleChunk(3, -4, heights, slopes);

        assertEquals(70, sampler.baseHeight());
        for (int i = 0; i < 256; i++) {
            assertEquals(70, heights[i]);
            assertEquals(0.0f, slopes[i], 1e-6f);
        }
    }

    @Test
    void sampleChunk_MatchesHeightAtForEachColumn() {
        TerrainSpec spec = spec(null,
                List.of(layer("simplex", 25.0, 0.03)), List.of(), List.of());
        TerrainSampler sampler = sampler(spec, 11L);
        int[] heights = new int[256];
        float[] slopes = new float[256];

        sampler.sampleChunk(2, 5, heights, slopes);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(sampler.heightAt(2 * 16 + x, 5 * 16 + z), heights[z * 16 + x],
                        "column height mismatch at " + x + "," + z);
                assertTrue(slopes[z * 16 + x] >= 0.0f);
            }
        }
    }

    @Test
    void sampleChunk_RejectsWrongArraySizes() {
        TerrainSpec spec = spec(null,
                List.of(layer("fbm", 1.0, 0.01)), List.of(), List.of());
        TerrainSampler sampler = sampler(spec, 7L);
        assertThrows(IllegalArgumentException.class,
                () -> sampler.sampleChunk(0, 0, new int[10], new float[256]));
        assertThrows(IllegalArgumentException.class,
                () -> sampler.sampleChunk(0, 0, new int[256], new float[10]));
    }

    @Test
    void surfaceBlockAt_FirstMatchingRuleWinsAndRespectsBands() {
        TerrainSpec spec = spec(null,
                List.of(layer("fbm", 0.0, 0.01)), List.of(),
                List.of(
                        new TerrainSpec.SurfaceRule("minecraft:snow_block", 0, 200, 319, 0.0),
                        new TerrainSpec.SurfaceRule("minecraft:grass_block", 0, -64, 319, 0.0),
                        new TerrainSpec.SurfaceRule("minecraft:stone", 4, -64, 319, 0.0)));
        TerrainSampler sampler = sampler(spec, 7L);

        assertEquals("minecraft:snow_block", sampler.surfaceBlockAt(0, 250, 0.0));
        assertEquals("minecraft:grass_block", sampler.surfaceBlockAt(0, 100, 0.0));
        assertEquals("minecraft:stone", sampler.surfaceBlockAt(2, 100, 0.0));
        assertNull(sampler.surfaceBlockAt(5, 100, 0.0), "no rule covers depth 5");
    }

    @Test
    void surfaceBlockAt_RespectsMinimumSlope() {
        TerrainSpec spec = spec(null,
                List.of(layer("fbm", 0.0, 0.01)), List.of(),
                List.of(new TerrainSpec.SurfaceRule("minecraft:stone", 8, -64, 319, 0.7)));
        TerrainSampler sampler = sampler(spec, 7L);

        assertNull(sampler.surfaceBlockAt(0, 100, 0.5));
        assertEquals("minecraft:stone", sampler.surfaceBlockAt(0, 100, 0.8));
    }

    @Test
    void baseHeight_UsesSpecSeaLevelOverride() {
        TerrainSpec spec = spec(90,
                List.of(layer("fbm", 0.0, 0.01)), List.of(), List.of());
        assertEquals(90, sampler(spec, 7L).baseHeight());
    }

    @Test
    void waterEnabled_DefaultsToTrueAndCanBeDisabled() {
        TerrainSpec wet = new TerrainSpec(null, List.of(layer("fbm", 0.0, 0.01)),
                List.of(), List.of(), List.of(), null, true);
        TerrainSpec dry = new TerrainSpec(null, List.of(layer("fbm", 0.0, 0.01)),
                List.of(), List.of(), List.of(), null, false);
        assertTrue(sampler(wet, 7L).waterEnabled());
        assertFalse(sampler(dry, 7L).waterEnabled());
    }

    // ---------- 3D density ----------

    @Test
    void density_ThresholdBelowZeroFillsWholeColumn() {
        TerrainSampler sampler = sampler(densitySpec(-1, List.of()), 7L);
        DensityField field = sampler.sampleDensity(0, 0, -64, 320);

        assertTrue(field.isSolid(0, -64, 0));
        assertTrue(field.isSolid(15, 319, 15));
        assertEquals(319, field.topSolid(3, 3));
    }

    @Test
    void density_ThresholdAboveZeroLeavesVoid() {
        TerrainSampler sampler = sampler(densitySpec(1, List.of()), 7L);
        DensityField field = sampler.sampleDensity(0, 0, -64, 320);

        assertFalse(field.isSolid(0, 100, 0));
        assertEquals(-65, field.topSolid(0, 0));
    }

    @Test
    void density_ThresholdSplineCarvesAFloatingBand() {
        // Bias is +1 near the build limits and -1 at y=180, so a zero density fills only a band.
        TerrainSpec spec = densitySpec(0, List.of(
                new TerrainSpec.SplinePoint(-64, 1.0),
                new TerrainSpec.SplinePoint(180, -1.0),
                new TerrainSpec.SplinePoint(319, 1.0)));
        TerrainSampler sampler = sampler(spec, 7L);
        DensityField field = sampler.sampleDensity(0, 0, -64, 320);

        assertFalse(field.isSolid(0, -64, 0), "the bottom must be carved away");
        assertFalse(field.isSolid(0, 300, 0), "the top must be carved away");
        assertTrue(field.isSolid(0, 180, 0), "the island band must be solid");
        int top = field.topSolid(0, 0);
        assertTrue(top > 180 && top < 319, "the top solid block must sit inside the band, got " + top);
    }

    @Test
    void density_IsDeterministicForTheSameSeed() {
        TerrainSpec.Density density = new TerrainSpec.Density(
                List.of(layer("fbm3", 1.0, 0.02)), 0.1, 4, 4, List.of());
        TerrainSpec spec = new TerrainSpec(null, List.of(layer("fbm", 0.0, 0.01)),
                List.of(), List.of(), List.of(), density, true);
        DensityField first = sampler(spec, 21L).sampleDensity(0, 0, -64, 320);
        DensityField second = sampler(spec, 21L).sampleDensity(0, 0, -64, 320);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(first.topSolid(x, z), second.topSolid(x, z),
                        "same seed must reproduce the density field");
            }
        }
    }

    @Test
    void density_OutsideBuildRangeIsNeverSolid() {
        TerrainSampler sampler = sampler(densitySpec(-1, List.of()), 7L);
        DensityField field = sampler.sampleDensity(0, 0, -64, 320);

        assertFalse(field.isSolid(0, -65, 0));
        assertFalse(field.isSolid(0, 320, 0));
    }

    @Test
    void density_PlatformForcesSolidSpawnIsland() {
        TerrainSpec.Density density = new TerrainSpec.Density(
                List.of(layer("fbm3", 0.0, 0.02)), 1.0, 4, 4, List.of());
        TerrainSpec.Platform platform = new TerrainSpec.Platform(0, 0, 6, 170, 182);
        TerrainSpec spec = new TerrainSpec(null, List.of(layer("fbm", 0.0, 0.01)),
                List.of(), List.of(), List.of(platform), density, true);
        DensityField field = sampler(spec, 7L).sampleDensity(0, 0, -64, 320);

        // Threshold 1.0 with zero noise means the only solid blocks come from the platform.
        assertTrue(field.isSolid(0, 180, 0));
        assertEquals(182, field.topSolid(0, 0));
        assertFalse(field.isSolid(7, 180, 0), "outside the radius must stay void");
        assertFalse(field.isSolid(0, 169, 0), "below bottomY must stay void");
    }

    @Test
    void heightAt_PlatformRaisesTheColumn() {
        TerrainSpec.Platform platform = new TerrainSpec.Platform(0, 0, 4, 60, 70);
        TerrainSpec spec = new TerrainSpec(null, List.of(layer("fbm", 0.0, 0.01)),
                List.of(), List.of(), List.of(platform), null, true);
        TerrainSampler sampler = sampler(spec, 7L);

        assertEquals(70, sampler.heightAt(0, 0));
        assertEquals(70, sampler.heightAt(4, 0));
        assertEquals(63, sampler.heightAt(5, 0), "outside the radius the normal surface applies");
    }

    @Test
    void density_RejectsMissingSection() {
        TerrainSpec spec = spec(null,
                List.of(layer("fbm", 1.0, 0.01)), List.of(), List.of());
        TerrainSampler sampler = sampler(spec, 7L);

        assertFalse(sampler.hasDensity());
        assertThrows(IllegalStateException.class,
                () -> sampler.sampleDensity(0, 0, -64, 320));
    }
}
