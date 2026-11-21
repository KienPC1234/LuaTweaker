package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** addFeature defaults keep the legacy ore/placed shapes. */
class WorldgenFeatureShapeTest {

    private static final String RUBY_VEIN = "mymod:ruby_vein";
    private static final String CONFIGURED_PATH = WorldgenFixture.CONFIGURED_DIR + "mymod_ruby_vein.json";
    private static final String PLACED_PATH = WorldgenFixture.PLACED_DIR + "mymod_ruby_vein.json";

    WorldgenServiceImpl service;
    Map<String, String> files;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
        service.addFeature(RUBY_VEIN, WorldgenFixture.featureSpec());
        files = service.buildBiomeModifierFiles();
    }

    @Test
    void defaultConfiguredUsesOreType() {
        String json = files.get(CONFIGURED_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"minecraft:ore\"",
                "\"discard_chance_on_air_exposure\":0.0"),
            "the default configured feature must stay a plain ore with no discard: " + json);
    }

    @Test
    void defaultPlacedUsesCount() {
        String json = files.get(PLACED_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"minecraft:count\"",
                "\"type\":\"minecraft:uniform\""),
            "the default placement must be a uniform count: " + json);
    }

    @Test
    void defaultPlacedOmitsRarityFilter() {
        assertTrue(JsonFragments.containsNone(files.get(PLACED_PATH), "rarity_filter"),
            "the default placement must not add a rarity filter");
    }

    @Test
    void defaultPlacedOmitsScatteredOre() {
        assertTrue(JsonFragments.containsNone(files.get(PLACED_PATH), "scattered_ore"),
            "the default placement must not use the scattered ore type");
    }

    @Test
    void defaultPlacedOmitsInner() {
        assertTrue(JsonFragments.containsNone(files.get(PLACED_PATH), "\"inner\""),
            "the default placement must not add an inner height bias");
    }
}
