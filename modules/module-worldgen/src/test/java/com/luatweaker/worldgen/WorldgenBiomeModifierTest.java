package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** modifyBiome add-feature modifiers: path layout and emitted JSON. */
class WorldgenBiomeModifierTest {

    private static final String PLAINS = "minecraft:plains";
    private static final String FOREST = "minecraft:forest";
    private static final String RUBY_STONE = "mymod:ruby_stone";
    private static final String GLOWING_BERRIES = "mymod:glowing_berries";
    private static final String UNDERGROUND_ORES = "underground_ores";
    private static final String VEGETAL_DECORATION = "vegetal_decoration";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    private FakeLuaTable addFeaturesOptions() {
        FakeLuaTable addEntry = new FakeLuaTable()
            .put("feature", RUBY_STONE)
            .put("step", UNDERGROUND_ORES);
        return new FakeLuaTable()
            .put("biomes", FakeLuaTables.listOf(PLAINS, FOREST))
            .put("add", FakeLuaTables.listOf(addEntry));
    }

    private FakeLuaTable groupedOptions() {
        FakeLuaTable first = new FakeLuaTable().put("feature", RUBY_STONE).put("step", UNDERGROUND_ORES);
        FakeLuaTable second = new FakeLuaTable()
            .put("feature", GLOWING_BERRIES).put("step", VEGETAL_DECORATION);
        return new FakeLuaTable()
            .put("biome", PLAINS)
            .put("add", FakeLuaTables.listOf(first, second));
    }

    @Test
    void modifyBiomeStoresOneModification() {
        service.modifyBiome(PLAINS, addFeaturesOptions());

        assertEquals(1, service.getPendingBiomeMods().size(),
            "modifyBiome must record exactly one pending modification");
    }

    @Test
    void modifyBiomeEmitsOneFile() {
        service.modifyBiome(PLAINS, addFeaturesOptions());

        assertEquals(1, service.buildBiomeModifierFiles().size(),
            "a single-step add modifier must emit one file");
    }

    @Test
    void modifierPathLivesUnderBiomeModifierDirectory() {
        service.modifyBiome(PLAINS, addFeaturesOptions());

        String path = service.buildBiomeModifierFiles().keySet().iterator().next();

        assertEquals(List.of(true, true),
            List.of(path.startsWith(WorldgenFixture.MODIFIER_DIR), path.endsWith(".json")),
            "the modifier path must live under the biome_modifier directory, got: " + path);
    }

    @Test
    void addFeaturesJsonDescribesModifier() {
        service.modifyBiome(PLAINS, addFeaturesOptions());

        String json = service.buildBiomeModifierFiles().values().iterator().next();

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"neoforge:add_features\"",
                "\"biomes\":[\"minecraft:plains\",\"minecraft:forest\"]",
                "\"features\":[\"mymod:ruby_stone\"]",
                "\"step\":\"underground_ores\""),
            "the add_features modifier must pin its type, biomes, features and step: " + json);
    }

    @Test
    void addsAreGroupedIntoOneFilePerStep() {
        service.modifyBiome(PLAINS, groupedOptions());

        assertEquals(2, service.buildBiomeModifierFiles().size(),
            "one modifier file must be emitted per generation step");
    }

    @Test
    void eachStepHasItsOwnModifierFile() {
        service.modifyBiome(PLAINS, groupedOptions());

        Map<String, String> files = service.buildBiomeModifierFiles();

        assertTrue(
            files.values().stream().anyMatch(json -> json.contains("\"step\":\"underground_ores\""))
                && files.values().stream().anyMatch(json -> json.contains("\"step\":\"vegetal_decoration\""))
                && files.values().stream().anyMatch(json -> json.contains("\"features\":[\"mymod:glowing_berries\"]")),
            "each step must get a modifier naming its own features");
    }
}
