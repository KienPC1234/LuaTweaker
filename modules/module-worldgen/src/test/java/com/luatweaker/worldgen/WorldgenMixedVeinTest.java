package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** addMixedVein emits the GTNH-style vein feature set. */
class WorldgenMixedVeinTest {

    private static final String MAGNETITE_VEIN = "mymod:magnetite_vein";
    private static final String CONFIGURED_PATH = WorldgenFixture.CONFIGURED_DIR + "mymod_magnetite_vein.json";
    private static final String PLACED_PATH = WorldgenFixture.PLACED_DIR + "mymod_magnetite_vein.json";
    private static final String MODIFIER_PATH = WorldgenFixture.MODIFIER_DIR + "add_mymod_magnetite_vein.json";

    WorldgenServiceImpl service;
    Map<String, String> files;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
        service.addMixedVein(MAGNETITE_VEIN, WorldgenFixture.magnetiteVeinSpec());
        files = service.buildMixedVeinFiles();
    }

    @Test
    void mixedVeinStoresOnePendingVein() {
        assertEquals(1, service.getPendingMixedVeins().size(),
            "addMixedVein must record exactly one pending vein");
    }

    @Test
    void mixedVeinEmitsThreeFiles() {
        assertEquals(3, files.size(), "a mixed vein must emit configured, placed and modifier files");
    }

    @Test
    void mixedVeinConfiguredDescribesOres() {
        String json = files.get(CONFIGURED_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"luatweaker:mixed_vein\"",
                "\"size\":32",
                "\"discard_chance_on_air_exposure\":0.0",
                "\"Name\":\"mymod:magnetite_ore\"",
                "\"Name\":\"mymod:vanadium_magnetite_ore\"",
                "\"Name\":\"mymod:gold_ore\"",
                "\"weight\":70",
                "\"weight\":20",
                "\"weight\":10",
                "minecraft:stone_ore_replaceables",
                "minecraft:deepslate_ore_replaceables"),
            "the mixed vein must pin its type, size, every ore, every weight and both replaceables: " + json);
    }

    @Test
    void mixedVeinPlacedDescribesPlacement() {
        String json = files.get(PLACED_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"feature\":\"luatweaker:mymod_magnetite_vein\"",
                "\"count\":4",
                "\"type\":\"minecraft:uniform\""),
            "the placed mixed vein must pin the feature reference, count and uniform type: " + json);
    }

    @Test
    void mixedVeinModifierAddsFeature() {
        String json = files.get(MODIFIER_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"neoforge:add_features\"",
                "\"features\":[\"luatweaker:mymod_magnetite_vein\"]"),
            "the biome modifier must add the mixed vein placed feature: " + json);
    }

    @Test
    void mixedVeinIsCountedInSummary() {
        assertEquals(1, service.getModifications().get("mixedVeins"),
            "the summary must count every registered mixed vein");
    }
}
