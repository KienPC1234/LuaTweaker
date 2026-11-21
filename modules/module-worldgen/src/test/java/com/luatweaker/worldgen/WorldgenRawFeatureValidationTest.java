package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Validation of raw feature definitions. */
class WorldgenRawFeatureValidationTest {

    private static final String WEIRD = "mymod:weird";
    private static final String UPPERCASE = "UPPER:id";
    private static final String ONCE = "mymod:once";
    private static final String NOT_A_FEATURE = "{\"type\":\"minecraft:not_a_feature\"}";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void rawConfiguredRejectsUnknownType() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addRawConfiguredFeature(WEIRD, NOT_A_FEATURE),
            "a raw configured feature with an unknown type must be rejected");
    }

    @Test
    void rawConfiguredRejectsUppercaseId() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addRawConfiguredFeature(UPPERCASE, WorldgenRawFeatureTest.RAW_TREE),
            "a raw feature id with uppercase characters must be rejected");
    }

    @Test
    void rawConfiguredRejectsDuplicateId() {
        service.addRawConfiguredFeature(ONCE, WorldgenRawFeatureTest.RAW_TREE);

        assertThrows(IllegalArgumentException.class,
            () -> service.addRawConfiguredFeature(ONCE, WorldgenRawFeatureTest.RAW_TREE),
            "registering the same raw feature id twice must be rejected");
    }

    @Test
    void rawConfiguredRejectsFeatureIdCollision() {
        service.addRawConfiguredFeature(ONCE, WorldgenRawFeatureTest.RAW_TREE);

        assertThrows(IllegalArgumentException.class,
            () -> service.addFeature(ONCE, WorldgenFixture.featureSpec()),
            "a feature id already used by a raw configured feature must be rejected");
    }

    @Test
    void clearAllEmptiesRawFiles() {
        service.addRawConfiguredFeature(ONCE, WorldgenRawFeatureTest.RAW_TREE);
        service.addRawPlacedFeature(ONCE, WorldgenRawFeatureTest.RAW_PLACED);

        service.clearAll();

        assertEquals(List.of(true, true, true),
            List.of(
                service.getPendingRawConfigured().isEmpty(),
                service.getPendingRawPlaced().isEmpty(),
                service.buildRawFiles().isEmpty()),
            "clearAll must drop every pending raw feature and emit no raw files");
    }
}
