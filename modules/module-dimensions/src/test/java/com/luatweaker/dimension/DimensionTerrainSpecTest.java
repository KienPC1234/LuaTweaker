package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.noise.NoiseServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies declarative terrain specs: mode switch, native sampler and noise-module need. */
class DimensionTerrainSpecTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:spec";

    @Test
    void terrainSpecSetsSpecModeAndStoresSpec() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setTerrainSpec(DIMENSION_ID, specTable());

        assertEquals(List.of(DimensionServiceImpl.TerrainMode.SPEC, true, 1),
                List.of(service.getTerrainMode(DIMENSION_ID),
                        service.hasTerrainSpec(DIMENSION_ID),
                        service.getTerrainSpec(DIMENSION_ID).heightLayers().size()),
                "a terrain spec must switch the mode to spec and store the parsed layers");
    }

    @Test
    void terrainSpecRejectsUnknownDimension() {
        assertTrue(DimensionExceptions.illegalArgumentMessage(
                        () -> service.setTerrainSpec("minecraft:unknown", specTable()))
                        .contains("Unknown dimension"),
                "installing a terrain spec on an unknown dimension must be rejected");
    }

    @Test
    void terrainSpecSamplerRequiresNoiseModule() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setTerrainSpec(DIMENSION_ID, specTable());
        LuaServiceRegistry.clear();

        assertTrue(DimensionExceptions.illegalStateMessage(
                        () -> service.createTerrainSampler(DIMENSION_ID))
                        .contains("Noise"),
                "creating a spec sampler without the noise module must fail loudly");
    }

    @Test
    void terrainSpecSamplerIsCreated() {
        assertNotNull(samplerWithNoise(),
                "a terrain spec must produce a native sampler when the noise module is registered");
    }

    @Test
    void terrainSpecSamplerKeepsHeightsWithinAmplitude() {
        TerrainSampler sampler = samplerWithNoise();
        int[] heights = new int[256];
        float[] slopes = new float[256];
        sampler.sampleChunk(0, 0, heights, slopes);

        for (int height : heights) {
            assertTrue(height >= 43 && height <= 83,
                    "spec height must stay within seaLevel +/- amplitude, got " + height);
        }
    }

    @Test
    void terrainSpecSamplerUsesConfiguredSurface() {
        assertEquals("minecraft:stone", samplerWithNoise().surfaceBlockAt(0, 100, 0.0),
                "the spec sampler must apply the configured surface rule");
    }

    private TerrainSampler samplerWithNoise() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setTerrainSpec(DIMENSION_ID, specTable());
        LuaServiceRegistry.register("NoiseServiceImpl", new NoiseServiceImpl(4242L));
        return service.createTerrainSampler(DIMENSION_ID);
    }

    private ILuaTable specTable() {
        ILuaTable layer = engine.createTable();
        layer.rawset("kind", "fbm");
        layer.rawset("amp", 20.0);
        layer.rawset("frequency", 0.01);
        ILuaTable layers = engine.createTable();
        layers.rawset(1, layer);
        ILuaTable rule = engine.createTable();
        rule.rawset("block", "minecraft:stone");
        ILuaTable rules = engine.createTable();
        rules.rawset(1, rule);
        ILuaTable spec = engine.createTable();
        spec.rawset("height", layers);
        spec.rawset("surface", rules);
        return spec;
    }
}
