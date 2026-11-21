package com.luatweaker.structures;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureUnregisterTest {

    private static final String TEMPLE_ID = "mymod:ancient_temple";
    private static final String TEMPLE_TEMPLATE = "mymod:structures/temple";
    private static final String UNKNOWN_ID = "mymod:never_registered";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private StructureTestFixture fixture;

    @BeforeEach
    void setup() {
        fixture = new StructureTestFixture();
    }

    private void registerTemple() {
        fixture.service.register(TEMPLE_ID, fixture.fullConfig(TEMPLE_TEMPLATE));
    }

    @Test
    void unregisterReturnsTrueForRegisteredStructure() {
        registerTemple();

        assertTrue(fixture.service.unregister(TEMPLE_ID),
            "unregister must report true when the structure existed");
    }

    @Test
    void unregisterRemovesEntry() {
        registerTemple();
        fixture.service.unregister(TEMPLE_ID);

        assertNull(fixture.service.getEntry(TEMPLE_ID),
            "unregister must drop the stored entry");
    }

    @Test
    void unregisterReturnsFalseOnSecondCall() {
        registerTemple();
        fixture.service.unregister(TEMPLE_ID);

        assertFalse(fixture.service.unregister(TEMPLE_ID),
            "a second unregister must report the structure as absent");
    }

    @Test
    void unregisterReturnsFalseForUnknownStructure() {
        assertFalse(fixture.service.unregister(UNKNOWN_ID),
            "unregistering an id that was never registered must report false");
    }

    @Test
    void unregisterRejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.unregister(""),
            "unregister must reject a blank structure id");
    }
}
