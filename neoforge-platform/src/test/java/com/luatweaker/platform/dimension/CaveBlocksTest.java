package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.noise.NoiseLuaBinding;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Carved blocks must come out as air while the surface and floor stay solid. */
class CaveBlocksTest {

    private static final String CAVE_BLOCKS_ID = "minecraft:cave_blocks";
    private static final int SURFACE_Y = 100;

    private record BlocksContext(ILuaEngine engine, DimensionConfig config, NoiseColumn column) {
    }

    private static BlocksContext blocksContext() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NoiseLuaBinding.registerBindings(engine);
        DimensionServiceImpl service = new DimensionServiceImpl(engine);
        LuaServiceRegistry.register("DimensionServiceImpl", service);
        service.create(CAVE_BLOCKS_ID, engine.createTable());
        engine.executeStringOrThrow(
                "blockTerrain = function(cx, cz)\n"
                        + "    local heights, surfaces = {}, {}\n"
                        + "    for i = 1, 256 do\n"
                        + "        heights[i] = 100\n"
                        + "        surfaces[i] = 'minecraft:grass_block'\n"
                        + "    end\n"
                        + "    return heights, surfaces\n"
                        + "end\n"
                        + "blockCaveSpec = { density = { layers = { { kind = 'fbm3', amp = 1.0,"
                        + " frequency = 0.05 } }, threshold = -10 } }\n",
                "cave_blocks_setup");
        service.setChunkTerrain(CAVE_BLOCKS_ID,
                engine.getGlobalEnvironment().rawget("blockTerrain"));
        service.setCaveSpec(CAVE_BLOCKS_ID,
                engine.getGlobalEnvironment().rawget("blockCaveSpec"));

        DimensionConfig config = service.getConfig(CAVE_BLOCKS_ID);
        LuaChunkGenerator generator = new LuaChunkGenerator(new ChunkProbeTest.EmptyBiomeSource(),
                CAVE_BLOCKS_ID);
        LevelHeightAccessor heightAccessor = new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return config.maxHeight() - config.minHeight();
            }

            @Override
            public int getMinBuildHeight() {
                return config.minHeight();
            }
        };
        NoiseColumn column = generator.getBaseColumn(0, 0, heightAccessor, null);
        return new BlocksContext(engine, config, column);
    }

    @Test
    void columnSurfaceStaysSolid() {
        BlocksContext context = blocksContext();
        try {
            assertFalse(context.column().getBlock(SURFACE_Y).isAir(),
                    "the column surface must stay solid");
        } finally {
            LuaServiceRegistry.clear();
            context.engine().shutdown();
        }
    }

    @Test
    void belowSurfaceIsCarved() {
        BlocksContext context = blocksContext();
        try {
            assertTrue(context.column().getBlock(60).isAir(),
                    "a block below the surface guard must be carved");
        } finally {
            LuaServiceRegistry.clear();
            context.engine().shutdown();
        }
    }

    @Test
    void worldFloorStaysSolid() {
        BlocksContext context = blocksContext();
        try {
            assertFalse(context.column().getBlock(context.config().minHeight() + 1).isAir(),
                    "the world floor must stay solid");
        } finally {
            LuaServiceRegistry.clear();
            context.engine().shutdown();
        }
    }
}
