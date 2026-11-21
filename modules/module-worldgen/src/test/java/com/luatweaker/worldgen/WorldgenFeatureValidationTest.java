package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation of addFeature specs that fail before any file is emitted. */
class WorldgenFeatureValidationTest {

    private static final String RUBY_VEIN = "mymod:ruby_vein";
    private static final String RUBY_ORE = WorldgenFixture.RUBY_ORE;
    private static final String PLAINS = WorldgenFixture.PLAINS;

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void addFeatureRejectsDuplicateId() {
        service.addFeature(RUBY_VEIN, WorldgenFixture.featureSpec());

        String message = WorldgenFixture.errorMessage(
            () -> service.addFeature(RUBY_VEIN, WorldgenFixture.featureSpec()));

        assertTrue(message.contains("already registered"),
            "a duplicate feature id must be reported as already registered, got: " + message);
    }

    @Test
    void addFeatureRejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addFeature("", WorldgenFixture.featureSpec()),
            "a blank feature id must be rejected");
    }

    @Test
    void addFeatureRejectsMutuallyExclusiveCountAndRarity() {
        FakeLuaTable spec = WorldgenFixture.featureSpec().put("count", 10).put("rarity", 9);

        String message = WorldgenFixture.errorMessage(() -> service.addFeature(RUBY_VEIN, spec));

        assertTrue(message.contains("mutually exclusive"),
            "count and rarity together must be rejected as mutually exclusive, got: " + message);
    }

    @Test
    void addFeatureRejectsMissingBlock() {
        FakeLuaTable spec = new FakeLuaTable().put("biomes", PLAINS);

        assertThrows(IllegalArgumentException.class, () -> service.addFeature(RUBY_VEIN, spec),
            "a feature spec without a block must be rejected");
    }

    @Test
    void addFeatureRejectsEmptyBiomes() {
        FakeLuaTable spec = new FakeLuaTable()
            .put("block", RUBY_ORE)
            .put("biomes", FakeLuaTables.listOf());

        assertThrows(IllegalArgumentException.class, () -> service.addFeature(RUBY_VEIN, spec),
            "a feature spec with an empty biomes list must be rejected");
    }

    @Test
    void addFeatureRejectsBadHeightRange() {
        FakeLuaTable spec = WorldgenFixture.featureSpec().put("minHeight", 32).put("maxHeight", -64);

        assertThrows(IllegalArgumentException.class, () -> service.addFeature(RUBY_VEIN, spec),
            "a feature whose min height exceeds its max height must be rejected");
    }
}
