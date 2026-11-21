package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Dimension biome-tag queries and their validation. */
class WorldgenDimensionQueryTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String OVERWORLD_TAG = "#minecraft:is_overworld";
    private static final String CUSTOM_DIM = "mymod:deep";
    private static final String CUSTOM_TAG = "#mymod:is_deep";
    private static final String UNKNOWN_DIM = "mymod:unknown_dim";
    private static final String INVALID_DIM = "Bad Id!";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void registerRejectsBadInput() {
        assertEquals(List.of(true, true, true),
            List.of(
                WorldgenFixture.rejects(() -> service.registerDimensionBiomeTag("", OVERWORLD_TAG)),
                WorldgenFixture.rejects(() -> service.registerDimensionBiomeTag(CUSTOM_DIM, "")),
                WorldgenFixture.rejects(() -> service.registerDimensionBiomeTag(CUSTOM_DIM, "#not a tag!!"))),
            "a blank dimension, a blank tag and a malformed tag must all be rejected");
    }

    @Test
    void unknownDimensionResolvesToEmpty() {
        assertEquals(Optional.empty(), service.resolveDimensionBiomeTag(UNKNOWN_DIM),
            "an unknown dimension must resolve to an empty optional");
    }

    @Test
    void defaultTagIsReturned() {
        assertEquals(OVERWORLD_TAG, service.getDimensionBiomeTag(OVERWORLD),
            "getDimensionBiomeTag must return the built-in overworld tag");
    }

    @Test
    void registeredOverrideIsReturned() {
        service.registerDimensionBiomeTag(CUSTOM_DIM, CUSTOM_TAG);

        assertEquals(CUSTOM_TAG, service.getDimensionBiomeTag(CUSTOM_DIM),
            "getDimensionBiomeTag must return a registered custom tag");
    }

    @Test
    void unknownDimensionReturnsNull() {
        assertNull(service.getDimensionBiomeTag(UNKNOWN_DIM),
            "getDimensionBiomeTag must return null for an unknown dimension");
    }

    @Test
    void invalidDimensionIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.getDimensionBiomeTag(INVALID_DIM),
            "getDimensionBiomeTag must reject an invalid dimension id");
    }
}
