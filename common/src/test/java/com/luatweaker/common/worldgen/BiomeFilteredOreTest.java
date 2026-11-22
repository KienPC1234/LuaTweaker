package com.luatweaker.common.worldgen;

import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Biome-filtered ores must use the explicit biome list instead of the dimension tag. */
class BiomeFilteredOreTest {

    private static Map<String, String> filteredFiles() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOreBiomeFiltered("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10,
                new String[] {"minecraft:plains", "minecraft:forest"});
        return WorldgenFixtures.apply(service);
    }

    private static String addFeaturesModifier() {
        String modifier = filteredFiles().values().stream()
                .filter(json -> json.contains("neoforge:add_features"))
                .findFirst().orElse(null);

        assertNotNull(modifier,
                "a biome-filtered ore must emit an add_features biome modifier");
        return modifier;
    }

    @Test
    void modifierUsesTheExplicitBiomeList() {
        assertTrue(addFeaturesModifier().contains("\"biomes\":[\"minecraft:plains\",\"minecraft:forest\"]"),
                "the modifier must list the explicit biomes");
    }

    @Test
    void modifierIgnoresTheDimensionTag() {
        assertFalse(addFeaturesModifier().contains("is_overworld"),
                "explicit biome list must win over the dimension tag");
    }
}
