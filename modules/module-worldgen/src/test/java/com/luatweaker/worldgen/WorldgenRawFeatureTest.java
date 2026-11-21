package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Raw JSON passthrough for configured and placed features. */
class WorldgenRawFeatureTest {

    private static final String CRYSTAL_TREE = "mymod:crystal_tree";
    private static final String CRYSTAL_PATCH = "mymod:crystal_patch";
    private static final String BAD_PLACED = "mymod:bad";
    private static final String BROKEN_PLACED = "mymod:broken";

    static final String RAW_TREE = "{\"type\":\"minecraft:tree\",\"config\":{\"force_dirt\":false,"
        + "\"minimum_size\":{\"type\":\"minecraft:two_layers_feature_size\"}}}";
    static final String RAW_PLACED = "{\"feature\":\"luatweaker:mymod_crystal_tree\","
        + "\"placement\":[{\"type\":\"minecraft:biome\"}]}";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void rawConfiguredEmitsVerbatimFile() {
        service.addRawConfiguredFeature(CRYSTAL_TREE, RAW_TREE);

        assertEquals(RAW_TREE,
            service.buildRawFiles().get(WorldgenFixture.CONFIGURED_DIR + "mymod_crystal_tree.json"),
            "a raw configured feature must be emitted byte-for-byte as supplied");
    }

    @Test
    void rawConfiguredIsCountedInSummary() {
        service.addRawConfiguredFeature(CRYSTAL_TREE, RAW_TREE);

        assertEquals(1, service.getModifications().get("rawConfigured"),
            "the summary must count every raw configured feature");
    }

    @Test
    void rawPlacedEmitsOneFile() {
        service.addRawPlacedFeature(CRYSTAL_PATCH, RAW_PLACED);

        assertEquals(1, service.buildRawFiles().size(), "a raw placed feature must emit exactly one file");
    }

    @Test
    void rawPlacedIsCountedInSummary() {
        service.addRawPlacedFeature(CRYSTAL_PATCH, RAW_PLACED);

        assertEquals(1, service.getModifications().get("rawPlaced"),
            "the summary must count every raw placed feature");
    }

    @Test
    void rawPlacedRejectsMissingFeature() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addRawPlacedFeature(BAD_PLACED, "{\"placement\":[]}"),
            "a raw placed feature without a feature reference must be rejected");
    }

    @Test
    void rawPlacedRejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addRawPlacedFeature(BROKEN_PLACED, "{not json"),
            "a raw placed feature with malformed JSON must be rejected");
    }
}
