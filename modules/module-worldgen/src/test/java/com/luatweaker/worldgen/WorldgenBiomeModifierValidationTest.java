package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validation of modifyBiome options. */
class WorldgenBiomeModifierValidationTest {

    private static final String PLAINS = "minecraft:plains";
    private static final String ORE_DIRT = "minecraft:ore_dirt";
    private static final String RUBY_STONE = "mymod:ruby_stone";
    private static final String BIOMES_KEY = "biomes";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void rejectsBlankBiomeId() {
        FakeLuaTable options = new FakeLuaTable().put("biomes", PLAINS).put("remove", ORE_DIRT);

        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome("", options),
            "a blank biome id must be rejected");
    }

    @Test
    void rejectsEmptyBiomes() {
        FakeLuaTable options = new FakeLuaTable()
            .put(BIOMES_KEY, FakeLuaTables.listOf())
            .put("remove", ORE_DIRT);

        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome(PLAINS, options),
            "an empty biomes list must be rejected");
    }

    @Test
    void rejectsUnknownStep() {
        FakeLuaTable options = new FakeLuaTable()
            .put(BIOMES_KEY, PLAINS)
            .put("step", "magic_sparkles")
            .put("add", RUBY_STONE);

        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome(PLAINS, options),
            "an unknown generation step must be rejected");
    }

    @Test
    void rejectsAddWithoutRemove() {
        FakeLuaTable options = new FakeLuaTable().put(BIOMES_KEY, PLAINS);

        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome(PLAINS, options),
            "options with neither add nor remove must be rejected");
    }

    @Test
    void rejectsInvalidBiomeEntry() {
        FakeLuaTable options = new FakeLuaTable()
            .put(BIOMES_KEY, FakeLuaTables.listOf("not a biome!!"))
            .put("remove", ORE_DIRT);

        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome(PLAINS, options),
            "an invalid biome resource location must be rejected");
    }
}
