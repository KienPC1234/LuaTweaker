package com.luatweaker.worldgen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Built-in dimension biome tags and custom registrations. */
class WorldgenDimensionRegistryTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private static final String OVERWORLD_TAG = "#minecraft:is_overworld";
    private static final String NETHER_TAG = "#minecraft:is_nether";
    private static final String END_TAG = "#minecraft:is_end";
    private static final String CUSTOM_DIM = "twilightforest:twilight_forest";
    private static final String CUSTOM_TAG = "#twilightforest:is_twilight_forest";

    WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void overworldTagIsCorrect() {
        assertEquals(Optional.of(OVERWORLD_TAG), service.resolveDimensionBiomeTag(OVERWORLD),
            "the overworld must resolve to the overworld biome tag");
    }

    @Test
    void netherTagIsCorrect() {
        assertEquals(Optional.of(NETHER_TAG), service.resolveDimensionBiomeTag(NETHER),
            "the nether must resolve to the nether biome tag");
    }

    @Test
    void endTagIsCorrect() {
        assertEquals(Optional.of(END_TAG), service.resolveDimensionBiomeTag(END),
            "the end must resolve to the end biome tag");
    }

    @Test
    void knownDimensionsHasThreeEntries() {
        assertEquals(3, service.getKnownDimensions().size(),
            "the three vanilla dimensions must be known by default");
    }

    @Test
    void knownDimensionsMapsOverworldTag() {
        assertEquals(OVERWORLD_TAG, service.getKnownDimensions().get(OVERWORLD),
            "the known-dimensions map must expose the overworld tag");
    }

    @Test
    void registerReturnsTrue() {
        assertTrue(service.registerDimensionBiomeTag(CUSTOM_DIM, CUSTOM_TAG),
            "registerDimensionBiomeTag must report success for a valid registration");
    }

    @Test
    void resolvesRegisteredTag() {
        service.registerDimensionBiomeTag(CUSTOM_DIM, CUSTOM_TAG);

        assertEquals(Optional.of(CUSTOM_TAG), service.resolveDimensionBiomeTag(CUSTOM_DIM),
            "a registered dimension must resolve to its biome tag");
    }

    @Test
    void knownDimensionsGrowsByOne() {
        service.registerDimensionBiomeTag(CUSTOM_DIM, CUSTOM_TAG);

        assertEquals(4, service.getKnownDimensions().size(),
            "registering one custom dimension must add exactly one known entry");
    }
}
