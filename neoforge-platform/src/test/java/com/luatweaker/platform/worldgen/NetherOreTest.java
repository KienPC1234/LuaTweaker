package com.luatweaker.platform.worldgen;

import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nether ores must use the nether tag and the netherrack target. */
class NetherOreTest {

    private static Map<String, String> netherFiles() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOre("mymod:nether_ruby", "minecraft:the_nether", 0, 64, 6, 4);
        return WorldgenFixtures.apply(service);
    }

    private static String addFeaturesModifier() {
        String modifier = netherFiles().values().stream()
                .filter(json -> json.contains("neoforge:add_features"))
                .findFirst().orElse(null);

        assertNotNull(modifier,
                "a nether ore must emit an add_features biome modifier");
        return modifier;
    }

    private static String oreConfiguredFeature() {
        String configured = netherFiles().values().stream()
                .filter(json -> json.contains("\"type\":\"minecraft:ore\""))
                .findFirst().orElse(null);

        assertNotNull(configured,
                "a nether ore must emit a configured ore feature");
        return configured;
    }

    @Test
    void modifierTargetsTheNetherBiomeTag() {
        assertTrue(addFeaturesModifier().contains("\"biomes\":\"#minecraft:is_nether\""),
                "the modifier must target the nether biome tag");
    }

    @Test
    void configuredFeatureTargetsNetherrack() {
        assertTrue(oreConfiguredFeature().contains("\"block\":\"minecraft:netherrack\""),
                "the nether ore must target netherrack");
    }
}
