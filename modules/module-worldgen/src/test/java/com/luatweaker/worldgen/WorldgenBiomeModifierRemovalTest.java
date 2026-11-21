package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** modifyBiome remove-feature modifiers: plural steps key and tag biomes. */
class WorldgenBiomeModifierRemovalTest {

    private static final String PLAINS = "minecraft:plains";
    private static final String OVERWORLD_TAG = "#minecraft:is_overworld";
    private static final String ORE_DIRT = "minecraft:ore_dirt";
    private static final String UNDERGROUND_ORES = "underground_ores";
    private static final String VEGETAL_DECORATION = "vegetal_decoration";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    private FakeLuaTable removeOptions() {
        return new FakeLuaTable()
            .put("biomes", PLAINS)
            .put("remove", FakeLuaTables.listOf("minecraft:ore_coal_upper", "minecraft:ore_coal_lower"));
    }

    private FakeLuaTable tagOptions() {
        return new FakeLuaTable()
            .put("biomes", FakeLuaTables.listOf(OVERWORLD_TAG))
            .put("remove", ORE_DIRT)
            .put("removeSteps", FakeLuaTables.listOf(UNDERGROUND_ORES, VEGETAL_DECORATION));
    }

    @Test
    void removeEmitsSingleFile() {
        service.modifyBiome(PLAINS, removeOptions());

        assertEquals(1, service.buildBiomeModifierFiles().size(),
            "a single remove modifier must emit one file");
    }

    @Test
    void removeJsonDescribesRemoval() {
        service.modifyBiome(PLAINS, removeOptions());

        String json = service.buildBiomeModifierFiles().values().iterator().next();

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"neoforge:remove_features\"",
                "\"features\":[\"minecraft:ore_coal_upper\",\"minecraft:ore_coal_lower\"]",
                "\"steps\":[\"underground_ores\"]"),
            "the remove_features modifier must pin its type, features and steps: " + json);
    }

    @Test
    void removeUsesPluralStepsKey() {
        service.modifyBiome(PLAINS, removeOptions());

        String json = service.buildBiomeModifierFiles().values().iterator().next();

        assertTrue(JsonFragments.containsNone(json, "\"step\":"),
            "a remove modifier must use the plural steps key, got: " + json);
    }

    @Test
    void acceptsTagBiomes() {
        service.modifyBiome("mymod:rocky_plains", tagOptions());

        String json = service.buildBiomeModifierFiles().values().iterator().next();

        assertTrue(JsonFragments.containsAll(json, "\"biomes\":[\"#minecraft:is_overworld\"]"),
            "a biome tag must be accepted verbatim in the biomes list: " + json);
    }

    @Test
    void acceptsExplicitRemoveSteps() {
        service.modifyBiome("mymod:rocky_plains", tagOptions());

        String json = service.buildBiomeModifierFiles().values().iterator().next();

        assertTrue(JsonFragments.containsAll(json, "\"steps\":[\"underground_ores\",\"vegetal_decoration\"]"),
            "explicit removeSteps must be preserved in order: " + json);
    }
}
