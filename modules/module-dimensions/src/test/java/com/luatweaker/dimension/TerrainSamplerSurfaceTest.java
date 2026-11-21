package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies surface-rule selection, slope limits, sea level and water flags. */
class TerrainSamplerSurfaceTest extends TerrainSamplerTestBase {

    private static final String FBM = "fbm";
    private static final String STONE = "minecraft:stone";

    @Test
    void surfaceRuleFirstMatchWinsAndRespectsBands() {
        TerrainSpec spec = terrain(null,
                List.of(layer(FBM, 0.0, 0.01)), List.of(),
                List.of(
                        new TerrainSpec.SurfaceRule("minecraft:snow_block", 0, 200, 319, 0.0),
                        new TerrainSpec.SurfaceRule("minecraft:grass_block", 0, -64, 319, 0.0),
                        new TerrainSpec.SurfaceRule(STONE, 4, -64, 319, 0.0)));
        TerrainSampler sampler = sampler(spec, 7L);

        assertEquals(Arrays.asList("minecraft:snow_block", "minecraft:grass_block",
                        STONE, null),
                Arrays.asList(sampler.surfaceBlockAt(0, 250, 0.0),
                        sampler.surfaceBlockAt(0, 100, 0.0),
                        sampler.surfaceBlockAt(2, 100, 0.0),
                        sampler.surfaceBlockAt(5, 100, 0.0)),
                "the first matching surface rule must win and depth bands must be respected");
    }

    @Test
    void surfaceRuleRespectsMinimumSlope() {
        TerrainSpec spec = terrain(null,
                List.of(layer(FBM, 0.0, 0.01)), List.of(),
                List.of(new TerrainSpec.SurfaceRule(STONE, 8, -64, 319, 0.7)));
        TerrainSampler sampler = sampler(spec, 7L);

        assertEquals(Arrays.asList(null, STONE),
                Arrays.asList(sampler.surfaceBlockAt(0, 100, 0.5),
                        sampler.surfaceBlockAt(0, 100, 0.8)),
                "a surface rule with a minimum slope must only match slopes at or above it");
    }

    @Test
    void baseHeightUsesSpecSeaLevelOverride() {
        TerrainSpec spec = terrain(90,
                List.of(layer(FBM, 0.0, 0.01)), List.of(), List.of());

        assertEquals(90, sampler(spec, 7L).baseHeight(),
                "a spec sea level override must become the base height");
    }

    @Test
    void waterEnabledDefaultsTrueAndCanBeDisabled() {
        TerrainSpec wet = terrain(null, List.of(layer(FBM, 0.0, 0.01)), List.of(), List.of());
        TerrainSpec dry = new TerrainSpec(null, List.of(layer(FBM, 0.0, 0.01)),
                List.of(), List.of(), List.of(), null, false);

        assertEquals(List.of(true, false),
                List.of(sampler(wet, 7L).waterEnabled(), sampler(dry, 7L).waterEnabled()),
                "water must default to enabled and honour an explicit false flag");
    }

    @Test
    void sampleChunkProducesNonNegativeSlopes() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer(FBM, 25.0, 0.03)), List.of(), List.of()), 11L);
        int[] heights = new int[256];
        float[] slopes = new float[256];
        sampler.sampleChunk(2, 5, heights, slopes);

        for (int i = 0; i < slopes.length; i++) {
            assertTrue(slopes[i] >= 0.0f, "slope must be non-negative at index " + i);
        }
    }

    @Test
    void sampleChunkRejectsWrongArraySizes() {
        TerrainSampler sampler = sampler(terrain(null,
                List.of(layer(FBM, 1.0, 0.01)), List.of(), List.of()), 7L);
        List<Executable> invalidCalls = List.of(
                () -> sampler.sampleChunk(0, 0, new int[10], new float[256]),
                () -> sampler.sampleChunk(0, 0, new int[256], new float[10]));

        for (Executable call : invalidCalls) {
            assertThrows(IllegalArgumentException.class, call,
                    "sampleChunk must reject height or slope arrays that are not 256 long");
        }
    }
}
