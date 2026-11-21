package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.noise.NoiseLuaBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that a declarative terrain spec installed from Lua is sampled
 * natively (spline-mapped heights, configured surface) by the dimension service.
 */
class DimensionLuaTerrainSpecTest extends DimensionLuaTestBase {

    private static final String SPEC_ID = "minecraft:spec_dim";
    private static final String SPEC_SCRIPT =
            "Dimensions:Create('" + SPEC_ID + "', { seaLevel = 63 })\n"
                    + "Dimensions:SetTerrainSpec('" + SPEC_ID + "', {\n"
                    + "    height = { { kind = 'fbm', amp = 20, frequency = 0.01 } },\n"
                    + "    spline = { { -1, 40 }, { 1, 200 } },\n"
                    + "    surface = { { block = '" + STONE + "', maxDepth = 3 } },\n"
                    + "})\n";

    @Test
    void terrainSpecInfoReportsSpecMode() {
        engine.executeStringOrThrow(SPEC_SCRIPT, "lua_terrain_spec_fixture");
        ILuaTable info = (ILuaTable) dimensionService().getDimension(SPEC_ID);

        assertEquals(List.of("spec", true),
                List.of(info.rawget("terrainMode").asString(),
                        info.rawget("hasTerrainSpec").asBoolean()),
                "installing a terrain spec from Lua must report spec mode in the info table");
    }

    @Test
    void terrainSpecSamplerIsCreated() {
        assertNotNull(specSampler(),
                "the dimension service must build a native sampler from the Lua spec");
    }

    @Test
    void terrainSpecSamplerKeepsHeightsInSplineRange() {
        TerrainSampler sampler = specSampler();
        int[] heights = new int[256];
        float[] slopes = new float[256];
        sampler.sampleChunk(0, 0, heights, slopes);

        for (int height : heights) {
            assertTrue(height >= 40 && height <= 200,
                    "spline-mapped height out of range: " + height);
        }
    }

    @Test
    void terrainSpecSamplerUsesConfiguredStoneSurface() {
        assertEquals(STONE, specSampler().surfaceBlockAt(0, 120, 0.0),
                "the native sampler must apply the stone surface rule from the Lua spec");
    }

    private TerrainSampler specSampler() {
        engine.executeStringOrThrow(SPEC_SCRIPT, "lua_terrain_spec_fixture");
        NoiseLuaBinding.registerBindings(engine);
        return dimensionService().createTerrainSampler(SPEC_ID);
    }
}
