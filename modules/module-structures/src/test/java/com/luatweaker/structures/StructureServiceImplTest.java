package com.luatweaker.structures;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureServiceImplTest {

    private static final String TEMPLE_ID = "mymod:ancient_temple";
    private static final String TEMPLE_TEMPLATE = "mymod:structures/temple";
    private static final String TEMPLE_TEMPLATE_V2 = "mymod:structures/temple_v2";
    private static final String DESERT = "minecraft:desert";
    private static final String BADLANDS = "minecraft:badlands";
    private static final String TREASURE_LOOT = "mymod:chests/temple_treasure";
    private static final String RUIN_ID = "mymod:ruin";
    private static final String RUIN_TEMPLATE = "mymod:structures/ruin";
    private static final String PLAINS = "minecraft:plains";
    private static final String SKY_ID = "mymod:sky_temple";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private StructureTestFixture fixture;

    @BeforeEach
    void setup() {
        fixture = new StructureTestFixture();
    }

    @Test
    void registerStoresExactMapping() {
        fixture.service.register(TEMPLE_ID, fixture.fullConfig(TEMPLE_TEMPLATE));

        StructureServiceImpl.StructureEntry expected = new StructureServiceImpl.StructureEntry(
            TEMPLE_ID, TEMPLE_TEMPLATE, List.of(DESERT, BADLANDS), 32, 16, 0.8,
            Map.of("chest", TREASURE_LOOT), 0, 1, 80, false,
            "beard_thin", "WORLD_SURFACE_WG", "surface_structures",
            StructureServiceImpl.defaultSalt(TEMPLE_ID));

        assertEquals(expected, fixture.service.getEntry(TEMPLE_ID),
            "register must store every parsed field of the supplied config");
    }

    @Test
    void registerAppliesVanillaDefaults() {
        fixture.service.register(RUIN_ID, fixture.minimalConfig(RUIN_TEMPLATE));

        StructureServiceImpl.StructureEntry expected = new StructureServiceImpl.StructureEntry(
            RUIN_ID, RUIN_TEMPLATE, List.of(PLAINS), 32, 8, 1.0, Map.of(), 0, 1, 80, false,
            "beard_thin", "WORLD_SURFACE_WG", "surface_structures",
            StructureServiceImpl.defaultSalt(RUIN_ID));

        assertEquals(expected, fixture.service.getEntry(RUIN_ID),
            "omitted placement fields must fall back to the vanilla jigsaw defaults");
    }

    @Test
    void registerStoresCustomPlacement() {
        fixture.service.register(SKY_ID, fixture.customPlacementConfig(TEMPLE_TEMPLATE));

        StructureServiceImpl.StructureEntry expected = new StructureServiceImpl.StructureEntry(
            SKY_ID, TEMPLE_TEMPLATE, List.of(PLAINS), 64, 16, 1.0, Map.of(), 100, 6, 128, true,
            "bury", "OCEAN_FLOOR_WG", "surface_structures", 12_345);

        assertEquals(expected, fixture.service.getEntry(SKY_ID),
            "register must keep every custom placement value");
    }

    @Test
    void registerReplacesExistingEntry() {
        fixture.service.register(TEMPLE_ID, fixture.fullConfig(TEMPLE_TEMPLATE));
        fixture.service.register(TEMPLE_ID, fixture.minimalConfig(TEMPLE_TEMPLATE_V2));

        assertEquals(TEMPLE_TEMPLATE_V2, fixture.service.getEntry(TEMPLE_ID).template(),
            "re-registering an existing id must overwrite its template");
    }

    @Test
    void registerReplacesWithoutGrowingRegistry() {
        fixture.service.register(TEMPLE_ID, fixture.fullConfig(TEMPLE_TEMPLATE));
        fixture.service.register(TEMPLE_ID, fixture.minimalConfig(TEMPLE_TEMPLATE_V2));

        assertEquals(1, fixture.service.getRegistrations().get("structures"),
            "re-registering an existing id must not add a second registration");
    }
}
