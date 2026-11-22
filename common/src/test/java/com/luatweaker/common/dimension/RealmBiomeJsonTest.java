package com.luatweaker.common.dimension;

import com.luatweaker.dimension.BiomesServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Biomes module merges Lua entities into the virtual biome JSONs. */
class RealmBiomeJsonTest {

    private static final String PLAINS_BIOME_KEY = "data/luatweaker/worldgen/biome/crystal_plains.json";
    private static final String FOREST_BIOME_KEY = "data/luatweaker/worldgen/biome/crystal_forest.json";
    private static final String VOID_BIOME_KEY = "data/luatweaker/worldgen/biome/void_wastes.json";
    private static final String GOLEM_ID = "luatweaker:crystal_golem";

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        Object biomesObj = DimensionShowcaseFixture.getBiomeService();
        assertTrue(biomesObj instanceof BiomesServiceImpl,
                "BiomesServiceImpl must be registered");
    }

    private static String plainsBiome() {
        return DimensionShowcaseFixture.getDatapackService().getVirtualFiles().get(PLAINS_BIOME_KEY);
    }

    @Test
    void crystalPlainsBiomeExists() {
        assertNotNull(plainsBiome(),
                "crystal_plains biome JSON must exist");
    }

    @Test
    void golemIsAddedToPlainsSpawners() {
        assertTrue(plainsBiome().contains(GOLEM_ID),
                "crystal golem must be added to the crystal_plains biome spawners");
    }

    @Test
    void golemIsInTheMonsterCategory() {
        assertTrue(plainsBiome().contains("\"monster\""),
                "golem must be in the monster category");
    }

    @Test
    void crystalPlainsBiomeIsMaterialized() {
        assertTrue(DimensionShowcaseFixture.getDatapackService().getVirtualFiles()
                        .containsKey(PLAINS_BIOME_KEY),
                "crystal_plains biome must be materialized");
    }

    @Test
    void crystalForestBiomeIsMaterialized() {
        assertTrue(DimensionShowcaseFixture.getDatapackService().getVirtualFiles()
                        .containsKey(FOREST_BIOME_KEY),
                "crystal_forest biome must be materialized");
    }

    @Test
    void voidWastesBiomeIsMaterialized() {
        assertTrue(DimensionShowcaseFixture.getDatapackService().getVirtualFiles()
                        .containsKey(VOID_BIOME_KEY),
                "void_wastes biome must be materialized");
    }
}
