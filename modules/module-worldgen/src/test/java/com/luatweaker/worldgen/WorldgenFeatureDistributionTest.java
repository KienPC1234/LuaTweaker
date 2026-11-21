package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanilla-shaped distribution primitives: rarity, trapezoid anchors, scattered ore and inner bias. */
class WorldgenFeatureDistributionTest {

    private static final String DEEP_VEIN = "mymod:deep_vein";
    private static final String BIASED_VEIN = "mymod:biased_vein";
    private static final String BIASED_VEIN_TWO = "mymod:biased_vein2";
    private static final String PLAINS = "minecraft:plains";

    private static final String DEEP_CONFIGURED = WorldgenFixture.CONFIGURED_DIR + "mymod_deep_vein.json";
    private static final String DEEP_PLACED = WorldgenFixture.PLACED_DIR + "mymod_deep_vein.json";

    WorldgenServiceImpl service;
    Map<String, String> files;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
        FakeLuaTable spec = new FakeLuaTable()
            .put("block", "mymod:deep_ruby")
            .put("biomes", FakeLuaTables.listOf(PLAINS))
            .put("heightType", "trapezoid")
            .put("anchorMin", "above_bottom")
            .put("anchorMax", "absolute")
            .put("rarity", 9)
            .put("discardChance", 0.7)
            .put("scattered", true)
            .put("size", 12)
            .put("minHeight", -80)
            .put("maxHeight", 80);
        service.addFeature(DEEP_VEIN, spec);
        files = service.buildBiomeModifierFiles();
    }

    @Test
    void scatteredConfiguredDescribesOre() {
        String json = files.get(DEEP_CONFIGURED);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"minecraft:scattered_ore\"",
                "\"discard_chance_on_air_exposure\":0.7",
                "\"size\":12"),
            "a scattered vein must emit scattered_ore, its discard chance and size: " + json);
    }

    @Test
    void fullDistributionPlacedUsesRarity() {
        String json = files.get(DEEP_PLACED);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"minecraft:rarity_filter\"",
                "\"chance\":9"),
            "a rarity spec must emit a rarity_filter with its chance: " + json);
    }

    @Test
    void fullDistributionPlacedOmitsCount() {
        assertTrue(JsonFragments.containsNone(files.get(DEEP_PLACED), "\"count\""),
            "a rarity spec must not also emit a count modifier");
    }

    @Test
    void fullDistributionPlacedUsesTrapezoid() {
        String json = files.get(DEEP_PLACED);

        assertTrue(JsonFragments.containsAll(json,
                "\"type\":\"minecraft:trapezoid\"",
                "\"above_bottom\":-80",
                "\"absolute\":80"),
            "the trapezoid height provider must honour both anchors: " + json);
    }

    @Test
    void fullDistributionPlacedOmitsInner() {
        assertTrue(JsonFragments.containsNone(files.get(DEEP_PLACED), "\"inner\""),
            "a trapezoid height provider must not add an inner bias");
    }

    @Test
    void biasedHeightUsesVeryBiasedType() {
        service.addFeature(BIASED_VEIN, biasedSpec());

        String json = service.buildBiomeModifierFiles().get(WorldgenFixture.PLACED_DIR + "mymod_biased_vein.json");

        assertTrue(JsonFragments.containsAll(json, "\"type\":\"minecraft:very_biased_to_bottom\"", "\"inner\":8"),
            "very_biased_to_bottom must default to an inner bias of 8: " + json);
    }

    @Test
    void customInnerOverridesDefault() {
        service.addFeature(BIASED_VEIN_TWO, biasedSpec().put("heightType", "biased_to_bottom").put("inner", 4));

        String json = service.buildBiomeModifierFiles()
            .get(WorldgenFixture.PLACED_DIR + "mymod_biased_vein2.json");

        assertTrue(JsonFragments.containsAll(json, "\"inner\":4"),
            "an explicit inner value must replace the default: " + json);
    }

    private static FakeLuaTable biasedSpec() {
        return new FakeLuaTable()
            .put("block", WorldgenFixture.RUBY_ORE)
            .put("biomes", FakeLuaTables.listOf(PLAINS))
            .put("heightType", "very_biased_to_bottom");
    }
}
