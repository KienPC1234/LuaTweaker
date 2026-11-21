package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Verifies the classic surface/dirt/stone column and optional bedrock floor. */
class DimensionTerrainColumnTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void terrainColumnFallsBackToClassicStack() {
        service.create(DIMENSION_ID, engine.createTable());
        DimensionConfig config = service.getConfig(DIMENSION_ID);

        assertEquals(List.of("minecraft:grass_block", "minecraft:dirt", "minecraft:stone"),
                List.of(TerrainColumn.blockAtDepth(config, 70, 70),
                        TerrainColumn.blockAtDepth(config, 70, 69),
                        TerrainColumn.blockAtDepth(config, 70, 68)),
                "the classic column must be grass, dirt, then stone");
    }

    @Test
    void terrainColumnPlacesBedrockAtBottom() {
        DimensionConfig config = configWithBedrock();

        assertEquals("minecraft:bedrock", TerrainColumn.blockAtDepth(config, 63, config.minHeight()),
                "the lowest block must be bedrock when bedrock is enabled");
    }

    @Test
    void terrainColumnKeepsBedrockOnlyAtBottom() {
        DimensionConfig config = configWithBedrock();

        assertNotEquals("minecraft:bedrock",
                TerrainColumn.blockAtDepth(config, 63, config.minHeight() + 1),
                "bedrock must not extend above the bottom layer");
    }

    private DimensionConfig configWithBedrock() {
        ILuaTable config = engine.createTable();
        config.rawset("hasBedrock", true);
        service.create(DIMENSION_ID, config);
        return service.getConfig(DIMENSION_ID);
    }
}
