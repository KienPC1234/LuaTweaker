package com.luatweaker.structures;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureIdValidationTest {

    private static final String TEMPLE_TEMPLATE = "mymod:structures/temple";

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
    void registerRejectsBlankStructureId() {
        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register("", fixture.fullConfig(TEMPLE_TEMPLATE)),
            "a blank structure id must be rejected");
    }

    @Test
    void registerRejectsPaddedBlankStructureId() {
        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register("   ", fixture.fullConfig(TEMPLE_TEMPLATE)),
            "a whitespace-only structure id must be rejected");
    }

    @Test
    void registerRejectsUppercaseStructureId() {
        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register("UPPER:case", fixture.fullConfig(TEMPLE_TEMPLATE)),
            "a structure id with uppercase characters must be rejected");
    }

    @Test
    void unregisterRejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.unregister(""),
            "unregister must reject a blank structure id");
    }
}
