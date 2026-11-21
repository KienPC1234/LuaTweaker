package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end checks of the batch chunk terrain and batch biome callbacks through the
 * real Lua VM: one call per chunk, index layout, surfaces and overrides.
 */
class DimensionLuaBatchTerrainTest extends DimensionLuaTestBase {

    private static final String BATCH_ID = "minecraft:batch_dim";
    private static final String PLAINS = "minecraft:plains";
    private static final String OCEAN = "minecraft:ocean";
    private static final String CHUNK_TERRAIN_SCRIPT =
            "batchTerrainCalls = 0\n"
                    + "Dimensions:Create('" + BATCH_ID + "', { seaLevel = 64 })\n"
                    + "Dimensions:SetChunkTerrain('" + BATCH_ID + "', function(cx, cz, ctx)\n"
                    + "    batchTerrainCalls = batchTerrainCalls + 1\n"
                    + "    local heights, surfaces = {}, {}\n"
                    + "    for z = 0, 15 do\n"
                    + "        for x = 0, 15 do\n"
                    + "            local i = z * 16 + x + 1\n"
                    + "            heights[i] = ctx.seaLevel + x + z\n"
                    + "            surfaces[i] = '" + STONE + "'\n"
                    + "        end\n"
                    + "    end\n"
                    + "    return heights, surfaces,"
                    + " { [1] = { [80] = 'minecraft:gold_block' } }\n"
                    + "end)\n";
    private static final String BIOME_SCRIPT =
            "Dimensions:SetBiomeBatch('" + BATCH_ID + "', function(cx, cz, ctx)\n"
                    + "    local t = {}\n"
                    + "    for i = 1, 16 do t[i] = '" + PLAINS + "' end\n"
                    + "    t[6] = '" + OCEAN + "'\n"
                    + "    return t\n"
                    + "end)\n";

    @Test
    void batchTerrainCallbackRunsOncePerChunk() {
        installBatchTerrain();
        dimensionService().computeChunkTerrain(BATCH_ID, 2, 3);

        assertEquals(Arrays.asList(1, DimensionServiceImpl.TerrainMode.CHUNK, true),
                Arrays.asList(engine.getGlobalEnvironment().rawget("batchTerrainCalls").asInt(),
                        dimensionService().getTerrainMode(BATCH_ID),
                        dimensionService().hasChunkTerrain(BATCH_ID)),
                "the batch terrain callback must run once per chunk and report chunk mode");
    }

    @Test
    void batchTerrainHeightsUseXFastestLayout() {
        DimensionServiceImpl.ChunkTerrainResult result = batchResult();

        assertEquals(Arrays.asList(64, 65),
                Arrays.asList(result.heights()[0], result.heights()[1]),
                "batch heights must start at sea level with x as the fastest index");
    }

    @Test
    void batchTerrainSurfaceComesFromLua() {
        assertEquals(STONE, batchResult().surfaces()[0],
                "batch surfaces must use the block id returned by Lua");
    }

    @Test
    void batchTerrainOverridesComeFromLua() {
        assertEquals("minecraft:gold_block", batchResult().overrides().get(0).get(80),
                "batch overrides must use the column override returned by Lua");
    }

    @Test
    void batchBiomesFillQuartGrid() {
        installBatchBiomes();
        String[] ids = dimensionService().computeBiomeBatch(BATCH_ID, 2, 3);

        assertEquals(Arrays.asList(DimensionServiceImpl.BIOME_QUART_CELLS, OCEAN, PLAINS, true),
                Arrays.asList(ids.length, ids[5], ids[0],
                        dimensionService().hasBiomeBatch(BATCH_ID)),
                "the batch biome callback must fill 16 quart cells and be reported as installed");
    }

    private void installBatchTerrain() {
        engine.executeStringOrThrow(CHUNK_TERRAIN_SCRIPT, "lua_batch_terrain_fixture");
    }

    private DimensionServiceImpl.ChunkTerrainResult batchResult() {
        installBatchTerrain();
        return dimensionService().computeChunkTerrain(BATCH_ID, 2, 3);
    }

    private void installBatchBiomes() {
        engine.executeStringOrThrow(
                "Dimensions:Create('" + BATCH_ID + "', { seaLevel = 64 })\n" + BIOME_SCRIPT,
                "lua_batch_biome_fixture");
    }
}
