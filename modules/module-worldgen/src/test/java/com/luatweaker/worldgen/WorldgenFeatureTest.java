package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** addFeature emits a configured feature, a placed feature and a biome modifier. */
class WorldgenFeatureTest {

    private static final String RUBY_VEIN = "mymod:ruby_vein";
    private static final String NETHER_RUBY = "mymod:nether_ruby";
    private static final String NETHER_VEIN = "mymod:nether_vein";

    private static final String CONFIGURED_PATH = WorldgenFixture.CONFIGURED_DIR + "mymod_ruby_vein.json";
    private static final String PLACED_PATH = WorldgenFixture.PLACED_DIR + "mymod_ruby_vein.json";
    private static final String MODIFIER_PATH = WorldgenFixture.MODIFIER_DIR + "add_mymod_ruby_vein.json";
    private static final String NETHER_CONFIGURED_PATH = WorldgenFixture.CONFIGURED_DIR + "mymod_nether_vein.json";

    WorldgenServiceImpl service;
    Map<String, String> files;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
        service.addFeature(RUBY_VEIN, WorldgenFixture.featureSpec());
        files = service.buildBiomeModifierFiles();
    }

    @Test
    void addFeatureStoresOnePendingFeature() {
        assertEquals(1, service.getPendingFeatures().size(),
            "addFeature must record exactly one pending custom feature");
    }

    @Test
    void addFeatureEmitsThreeFiles() {
        assertEquals(3, files.size(), "addFeature must emit configured, placed and modifier files");
    }

    @Test
    void addFeatureEmitsExpectedPaths() {
        assertEquals(Set.of(CONFIGURED_PATH, PLACED_PATH, MODIFIER_PATH), files.keySet(),
            "the three emitted files must use the canonical worldgen paths");
    }

    @Test
    void configuredFeatureJsonDescribesOre() {
        String json = files.get(CONFIGURED_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"minecraft:ore\"",
                "\"size\":8",
                "minecraft:stone_ore_replaceables",
                "minecraft:deepslate_ore_replaceables",
                "\"Name\":\"mymod:ruby_ore\""),
            "the configured feature must pin the ore type, size, replaceables and block: " + json);
    }

    @Test
    void placedFeatureJsonDescribesPlacement() {
        String json = files.get(PLACED_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"feature\":\"luatweaker:mymod_ruby_vein\"",
                "\"count\":10",
                "\"absolute\":-64",
                "\"absolute\":32"),
            "the placed feature must pin the feature reference, count and height anchors: " + json);
    }

    @Test
    void modifierJsonAddsFeatureToBiomes() {
        String json = files.get(MODIFIER_PATH);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"neoforge:add_features\"",
                "\"biomes\":[\"minecraft:plains\"]",
                "\"features\":[\"luatweaker:mymod_ruby_vein\"]",
                "\"step\":\"underground_ores\""),
            "the biome modifier must add the placed feature to the declared biomes: " + json);
    }

    @Test
    void netherReplaceUsesNetherrack() {
        String json = netherConfiguredJson();

        assertTrue(JsonFragments.containsAll(json,
                "\"block\":\"minecraft:netherrack\"",
                "\"size\":4"),
            "replace = nether must target netherrack: " + json);
    }

    @Test
    void netherReplaceOmitsStoneReplaceables() {
        String json = netherConfiguredJson();

        assertTrue(JsonFragments.containsNone(json, "stone_ore_replaceables"),
            "a nether vein must not reference overworld stone replaceables: " + json);
    }

    private String netherConfiguredJson() {
        FakeLuaTable spec = new FakeLuaTable()
            .put("block", NETHER_RUBY)
            .put("biome", "minecraft:nether_wastes")
            .put("replace", "nether")
            .put("count", 6)
            .put("size", 4)
            .put("minHeight", 0)
            .put("maxHeight", 64)
            .put("step", "underground_decoration");
        service.addFeature(NETHER_VEIN, spec);
        return service.buildBiomeModifierFiles().get(NETHER_CONFIGURED_PATH);
    }
}
