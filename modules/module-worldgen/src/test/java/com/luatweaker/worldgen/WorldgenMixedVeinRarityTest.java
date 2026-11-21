package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Single-ore mixed veins, the rarity placement mode and the nether replace target. */
class WorldgenMixedVeinRarityTest {

    private static final String RUBY_VEIN = "mymod:ruby_vein";

    private static final String CONFIGURED_PATH = WorldgenFixture.CONFIGURED_DIR + "mymod_ruby_vein.json";
    private static final String PLACED_PATH = WorldgenFixture.PLACED_DIR + "mymod_ruby_vein.json";

    WorldgenServiceImpl service;
    Map<String, String> files;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
        FakeLuaTable spec = new FakeLuaTable()
            .put("ores", new FakeLuaTable().put("block", WorldgenFixture.RUBY_ORE))
            .put("biome", "minecraft:desert")
            .put("rarity", 16)
            .put("replace", "nether");
        service.addMixedVein(RUBY_VEIN, spec);
        files = service.buildMixedVeinFiles();
    }

    @Test
    void singleOreDefaultsWeightToOne() {
        assertTrue(JsonFragments.containsAll(files.get(CONFIGURED_PATH), "\"weight\":1"),
            "a single-ore vein must default the ore weight to 1");
    }

    @Test
    void netherReplaceUsesNetherrack() {
        assertTrue(JsonFragments.containsAll(files.get(CONFIGURED_PATH), "\"block\":\"minecraft:netherrack\""),
            "replace = nether must target netherrack");
    }

    @Test
    void rarityModeUsesRarityFilter() {
        assertTrue(JsonFragments.containsAll(files.get(PLACED_PATH),
                "\"type\":\"minecraft:rarity_filter\"", "\"chance\":16"),
            "a rarity spec must emit a rarity_filter with its chance");
    }

    @Test
    void rarityModeOmitsCount() {
        assertTrue(JsonFragments.containsNone(files.get(PLACED_PATH), "\"count\""),
            "a rarity spec must not also emit a count modifier");
    }
}
