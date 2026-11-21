package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the batch biome provider's 4x4 quart grid, fallbacks and precedence. */
class DimensionBiomeBatchTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:batch";
    private static final String PLAINS = "minecraft:plains";
    private static final String OCEAN = "minecraft:ocean";

    @Test
    void biomeBatchReturnsQuartGridWithFallbacks() {
        service.create(DIMENSION_ID, engine.createTable());
        engine.executeStringOrThrow(
                "function batchBiomes(cx, cz, ctx)\n"
                        + "    local t = {}\n"
                        + "    for i = 1, 16 do t[i] = 'minecraft:plains' end\n"
                        + "    t[2] = 'minecraft:ocean'\n"
                        + "    t[3] = 'Not A Biome'\n"
                        + "    t[4] = nil\n"
                        + "    return t\n"
                        + "end", "biome_batch_fixture");
        service.setBiomeBatch(DIMENSION_ID, engine.getGlobalEnvironment().rawget("batchBiomes"));

        String[] ids = service.computeBiomeBatch(DIMENSION_ID, 0, 0);

        assertEquals(Arrays.asList(DimensionServiceImpl.BIOME_QUART_CELLS, PLAINS, OCEAN, null, null,
                        true),
                Arrays.asList(ids.length, ids[0], ids[1], ids[2], ids[3],
                        service.hasBiomeBatch(DIMENSION_ID)),
                "batch biomes must fill 16 quart cells and fall back for invalid or nil ids");
    }

    @Test
    void biomeBatchIsClearedByPerColumnProvider() {
        service.create(DIMENSION_ID, engine.createTable());
        engine.executeStringOrThrow(
                "function batchBiomes(cx, cz, ctx)\n"
                        + "    local t = {}\n"
                        + "    for i = 1, 16 do t[i] = 'minecraft:plains' end\n"
                        + "    return t\n"
                        + "end\n"
                        + "function singleBiome(x, z) return 'minecraft:plains' end\n",
                "biome_switch_fixture");
        service.setBiomeBatch(DIMENSION_ID, engine.getGlobalEnvironment().rawget("batchBiomes"));

        service.setBiomeProvider(DIMENSION_ID, engine.getGlobalEnvironment().rawget("singleBiome"));

        assertEquals(List.of(false, 0),
                List.of(service.hasBiomeBatch(DIMENSION_ID),
                        service.computeBiomeBatch(DIMENSION_ID, 0, 0).length),
                "registering a per-column biome provider must clear the batch provider");
    }
}
