package com.luatweaker.structures;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StructurePlacementTest {

    private static final String RUIN_ID = "mymod:ruin";
    private static final String RUIN_TEMPLATE = "mymod:structures/ruin";
    private static final String UNKNOWN_ID = "mymod:ghost";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NO_PLACEMENT = "none";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private StructureTestFixture fixture;

    @BeforeEach
    void setup() {
        fixture = new StructureTestFixture();
    }

    private void registerRuin() {
        fixture.service.register(RUIN_ID, fixture.minimalConfig(RUIN_TEMPLATE));
    }

    @Test
    void placeAtRejectsUnknownStructure() {
        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.placeAt(UNKNOWN_ID, OVERWORLD, 0, 64, 0, NO_PLACEMENT, NO_PLACEMENT),
            "placing an unregistered structure must be rejected with a hint to register first");
    }

    @Test
    void placeAtRejectsBlankStructureId() {
        registerRuin();

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.placeAt("", OVERWORLD, 0, 64, 0, NO_PLACEMENT, NO_PLACEMENT),
            "a blank structure id must be rejected before lookup");
    }

    @Test
    void placeAtRejectsInvalidDimensionId() {
        registerRuin();

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.placeAt(RUIN_ID, "Bad Id!", 0, 64, 0, NO_PLACEMENT, NO_PLACEMENT),
            "an invalid dimension id must be rejected");
    }

    @Test
    void placeAtThrowsWhenPlatformUninitialized() {
        registerRuin();

        assertThrows(IllegalStateException.class,
            () -> fixture.service.placeAt(RUIN_ID, OVERWORLD, 0, 64, 0, NO_PLACEMENT, NO_PLACEMENT),
            "placing without an initialized Platform must fail loudly");
    }
}
