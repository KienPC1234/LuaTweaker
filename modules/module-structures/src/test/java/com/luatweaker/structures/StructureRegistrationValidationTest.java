package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureRegistrationValidationTest {

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

    private ILuaTable baseConfig() {
        return fixture.minimalConfig(STRUCTURE_TEMPLATE);
    }

    @Test
    void registerRejectsUnknownTerrainAdaptation() {
        ILuaTable config = fixture.withString(baseConfig(), "terrainAdaptation", "beard_fluffy");

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "an unknown terrainAdaptation must be rejected instead of dying at datapack load");
    }

    @Test
    void registerRejectsUnknownHeightmap() {
        ILuaTable config = fixture.withString(baseConfig(), "heightmap", "SKY");

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "an unknown heightmap must be rejected");
    }

    @Test
    void registerRejectsUnknownStep() {
        ILuaTable config = fixture.withString(baseConfig(), "step", "not_a_step");

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "an unknown terrain step must be rejected");
    }

    @Test
    void registerRejectsStartHeightOutOfRange() {
        ILuaTable config = fixture.withNumber(baseConfig(), "startHeight", 5_000);

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a startHeight outside the world height range must be rejected");
    }

    @Test
    void registerRejectsZeroSize() {
        ILuaTable config = fixture.withNumber(baseConfig(), "size", 0);

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a non-positive jigsaw size must be rejected");
    }

    @Test
    void registerRejectsNonBooleanExpansionHack() {
        ILuaTable config = fixture.withString(baseConfig(), "useExpansionHack", "yes");

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a non-boolean useExpansionHack must be rejected");
    }
}
