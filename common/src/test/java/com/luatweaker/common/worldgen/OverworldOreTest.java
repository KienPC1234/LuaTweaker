package com.luatweaker.common.worldgen;

import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Overworld ores must use the overworld tag and vanilla stone targets. */
class OverworldOreTest {

    private static final String RUBY_ORE = "mymod:ruby_ore";
    private static final String OVERWORLD = "minecraft:overworld";

    private static Map<String, String> overworldFiles() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOre(RUBY_ORE, OVERWORLD, -64, 32, 8, 10);
        return WorldgenFixtures.apply(service);
    }

    private static String addFeaturesModifier() {
        String modifier = overworldFiles().values().stream()
                .filter(json -> json.contains("neoforge:add_features"))
                .findFirst().orElse(null);

        assertNotNull(modifier,
                "an overworld ore must emit an add_features biome modifier");
        return modifier;
    }

    private static String oreConfiguredFeature() {
        String configured = overworldFiles().values().stream()
                .filter(json -> json.contains("minecraft:ore\"") || json.contains("\"type\":\"minecraft:ore\""))
                .findFirst().orElse(null);

        assertNotNull(configured,
                "an overworld ore must emit a configured ore feature");
        return configured;
    }

    @Test
    void emitsConfiguredPlacedAndModifierFiles() {
        assertTrue(overworldFiles().size() >= 3,
                "configured + placed + biome modifier expected");
    }

    @Test
    void modifierTargetsTheOverworldBiomeTag() {
        assertTrue(addFeaturesModifier().contains("\"biomes\":\"#minecraft:is_overworld\""),
                "the modifier must target the overworld biome tag");
    }

    @Test
    void modifierReferencesTheOreFeature() {
        assertTrue(addFeaturesModifier().contains("\"features\":[\"luatweaker:mymod_ruby_ore_ore_0\"]"),
                "the modifier must reference the placed ore feature");
    }

    @Test
    void configuredFeatureUsesVanillaFieldName() {
        assertTrue(oreConfiguredFeature().contains("discard_chance_on_air_exposure"),
                "vanilla field name required");
    }

    @Test
    void configuredFeatureTargetsStone() {
        assertTrue(oreConfiguredFeature().contains("minecraft:stone_ore_replaceables"),
                "the overworld ore must target vanilla stone");
    }

    @Test
    void configuredFeatureTargetsDeepslate() {
        assertTrue(oreConfiguredFeature().contains("minecraft:deepslate_ore_replaceables"),
                "the overworld ore must target deepslate");
    }

    @Test
    void configuredFeatureKeepsSize() {
        assertTrue(oreConfiguredFeature().contains("\"size\":8"),
                "the configured feature must keep the vein size");
    }
}
