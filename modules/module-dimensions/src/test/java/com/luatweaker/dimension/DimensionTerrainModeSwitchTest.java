package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies switching a dimension from batch chunk terrain back to a per-column generator. */
class DimensionTerrainModeSwitchTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:batch";

    @Test
    void terrainGeneratorSwitchesModeBackToLegacy() {
        service.create(DIMENSION_ID, engine.createTable());
        engine.executeStringOrThrow(
                "function batchChunkTerrain(cx, cz, ctx)\n"
                        + "    local h = {}\n"
                        + "    for i = 1, 256 do h[i] = 64 end\n"
                        + "    return h\n"
                        + "end\n"
                        + "function legacyGenerator(x, z, baseHeight) return baseHeight end\n",
                "mode_switch_fixture");
        service.setChunkTerrain(DIMENSION_ID, engine.getGlobalEnvironment().rawget("batchChunkTerrain"));
        DimensionServiceImpl.TerrainMode chunkMode = service.getTerrainMode(DIMENSION_ID);
        service.setTerrainGenerator(DIMENSION_ID, engine.getGlobalEnvironment().rawget("legacyGenerator"));

        assertEquals(List.of(DimensionServiceImpl.TerrainMode.CHUNK, DimensionServiceImpl.TerrainMode.LEGACY),
                List.of(chunkMode, service.getTerrainMode(DIMENSION_ID)),
                "installing a per-column generator must switch the mode back to legacy");
    }
}
