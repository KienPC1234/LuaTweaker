package com.luatweaker.common.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DensityField;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.dimension.TerrainSampler;
import com.luatweaker.noise.NoiseLuaBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cave carving must keep the surface and floor solid. */
class CaveSamplerTest {

    private static final String CAVE_PROBE_ID = "minecraft:cave_probe";
    private static final int SURFACE_Y = 100;

    private record CaveContext(ILuaEngine engine, DimensionConfig config, TerrainSampler sampler,
            DensityField caves) {
    }

    private static CaveContext caveContext() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NoiseLuaBinding.registerBindings(engine);
        DimensionServiceImpl service = new DimensionServiceImpl(engine);
        service.create(CAVE_PROBE_ID, engine.createTable());
        engine.executeStringOrThrow(
                "caveProbeSpec = { density = { layers = { { kind = 'fbm3', amp = 1.0,"
                        + " frequency = 0.05 } }, threshold = -10 } }\n",
                "cave_probe_spec");
        service.setCaveSpec(CAVE_PROBE_ID,
                engine.getGlobalEnvironment().rawget("caveProbeSpec"));

        DimensionConfig config = service.getConfig(CAVE_PROBE_ID);
        TerrainSampler sampler = service.createCaveSampler(CAVE_PROBE_ID);
        DensityField caves = sampler.sampleDensity(0, 0, config.minHeight(), config.maxHeight());
        return new CaveContext(engine, config, sampler, caves);
    }

    @Test
    void registeredSpecProducesASampler() {
        CaveContext context = caveContext();
        try {
            assertNotNull(context.sampler(),
                    "a registered cave spec must produce a sampler");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void deepStoneIsCarved() {
        CaveContext context = caveContext();
        try {
            assertTrue(LuaChunkGenerator.isCaveCarved(context.caves(), 4, SURFACE_Y - 10, 4,
                            SURFACE_Y, context.config().minHeight()),
                    "stone well below the surface must be carved");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void topBlocksStaySolid() {
        CaveContext context = caveContext();
        try {
            assertFalse(LuaChunkGenerator.isCaveCarved(context.caves(), 4, SURFACE_Y - 1, 4,
                            SURFACE_Y, context.config().minHeight()),
                    "the top blocks must stay solid for the heightmap");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void worldFloorStaysSolid() {
        CaveContext context = caveContext();
        try {
            assertFalse(LuaChunkGenerator.isCaveCarved(context.caves(), 4,
                            context.config().minHeight() + 1, 4, SURFACE_Y,
                            context.config().minHeight()),
                    "the world floor must stay solid");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void missingSpecCarvesNothing() {
        CaveContext context = caveContext();
        try {
            assertFalse(LuaChunkGenerator.isCaveCarved(null, 4, SURFACE_Y - 10, 4, SURFACE_Y,
                            context.config().minHeight()),
                    "without a cave spec nothing is carved");
        } finally {
            context.engine().shutdown();
        }
    }
}
