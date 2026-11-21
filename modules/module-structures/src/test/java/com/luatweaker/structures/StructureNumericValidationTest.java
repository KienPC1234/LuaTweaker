package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureNumericValidationTest {

    private static final String STRUCTURE_ID = "mymod:x";
    private static final String STRUCTURE_TEMPLATE = "mymod:structures/x";

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
    void registerRejectsSpacingBelowSeparation() {
        ILuaTable config = fixture.minimalConfig(STRUCTURE_TEMPLATE);
        fixture.withNumber(config, "spacing", 8);
        fixture.withNumber(config, "separation", 16);

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "spacing below separation must be rejected");
    }

    @Test
    void registerRejectsNegativeSpawnChance() {
        ILuaTable config = fixture.withNumber(fixture.minimalConfig(STRUCTURE_TEMPLATE), "spawnChance", -0.1);

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a negative spawnChance must be rejected");
    }

    @Test
    void registerRejectsSpawnChanceAboveOne() {
        ILuaTable config = fixture.withNumber(fixture.minimalConfig(STRUCTURE_TEMPLATE), "spawnChance", 1.5);

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a spawnChance above 1.0 must be rejected");
    }
}
