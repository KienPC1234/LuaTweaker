package com.luatweaker.common.worldgen;

import com.google.gson.JsonParser;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** modifyBiome and addFeature must materialize as valid modifier JSON. */
class ModifyBiomeTest {

    private static Map<String, String> modifyFiles() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        ILuaEngine engine = LuaEngineFactory.create(false);

        ILuaTable biomes = engine.createTable();
        biomes.rawset(1, "minecraft:plains");
        ILuaTable options = engine.createTable();
        options.rawset("biomes", biomes);
        options.rawset("remove", "minecraft:ore_dirt");
        service.modifyBiome("minecraft:plains", options);

        ILuaTable specBiomes = engine.createTable();
        specBiomes.rawset(1, "minecraft:plains");
        ILuaTable spec = engine.createTable();
        spec.rawset("block", "mymod:ruby_ore");
        spec.rawset("biomes", specBiomes);
        service.addFeature("mymod:ruby_vein", spec);

        return WorldgenFixtures.apply(service);
    }

    @Test
    void modifyBiomeEmitsAModifier() {
        assertTrue(modifyFiles().keySet().stream().anyMatch(key -> key.contains("biome_modifier/modify_")),
                "modifyBiome must emit a biome modifier file: " + modifyFiles().keySet());
    }

    @Test
    void addFeatureEmitsAModifier() {
        assertTrue(modifyFiles().keySet().stream().anyMatch(key -> key.contains("biome_modifier/add_")),
                "addFeature must emit a biome modifier file: " + modifyFiles().keySet());
    }

    @Test
    void addFeatureEmitsAConfiguredFeature() {
        assertTrue(modifyFiles().keySet().stream().anyMatch(key -> key.contains("configured_feature/")),
                "addFeature must emit a configured feature file: " + modifyFiles().keySet());
    }

    @Test
    void addFeatureEmitsAPlacedFeature() {
        assertTrue(modifyFiles().keySet().stream().anyMatch(key -> key.contains("placed_feature/")),
                "addFeature must emit a placed feature file: " + modifyFiles().keySet());
    }

    @Test
    void everyFileParsesAsJson() {
        Map<String, String> files = modifyFiles();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertDoesNotThrow(() -> JsonParser.parseString(entry.getValue()),
                    "generated JSON must parse: " + entry.getKey() + " = " + entry.getValue());
        }
    }

    @Test
    void addFeaturesUsesTheSingularStepKey() {
        assertTrue(modifyFiles().values().stream().anyMatch(json ->
                json.contains("\"type\":\"neoforge:add_features\"") && json.contains("\"step\":\"underground_ores\"")),
                "add_features uses the singular step key");
    }

    @Test
    void removeFeaturesUsesThePluralStepsList() {
        assertTrue(modifyFiles().values().stream().anyMatch(json ->
                json.contains("\"type\":\"neoforge:remove_features\"") && json.contains("\"steps\":[\"underground_ores\"]")),
                "remove_features uses the plural steps list");
    }
}
