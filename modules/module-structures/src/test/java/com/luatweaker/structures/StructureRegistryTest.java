package com.luatweaker.structures;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureRegistryTest {

    private static final String ID_A = "mymod:a";
    private static final String ID_B = "mymod:b";
    private static final String TEMPLATE_A = "mymod:structures/a";
    private static final String TEMPLATE_B = "mymod:structures/b";
    private static final String TEMPLE_ID = "mymod:ancient_temple";
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

    private void registerPair() {
        fixture.service.register(ID_B, fixture.minimalConfig(TEMPLATE_B));
        fixture.service.register(ID_A, fixture.minimalConfig(TEMPLATE_A));
    }

    private void registerTemple() {
        fixture.service.register(TEMPLE_ID, fixture.fullConfig(TEMPLE_TEMPLATE));
    }

    @Test
    void registrationsCountStructures() {
        registerPair();

        Map<String, Object> summary = fixture.service.getRegistrations();

        assertEquals(2, summary.get("structures"), "the summary must count every registration");
    }

    @Test
    void registrationsListSortedIds() {
        registerPair();

        Map<String, Object> summary = fixture.service.getRegistrations();

        assertEquals(List.of(ID_A, ID_B), summary.get("ids"), "the summary ids must be sorted");
    }

    @Test
    void structureIdsAreSorted() {
        registerPair();

        assertEquals(List.of(ID_A, ID_B), fixture.service.getStructureIds(),
            "getStructureIds must return every id in sorted order");
    }

    @Test
    void clearAllRemovesEntry() {
        registerTemple();
        fixture.service.clearAll();

        assertNull(fixture.service.getEntry(TEMPLE_ID), "clearAll must drop every stored entry");
    }

    @Test
    void clearAllEmptiesStructureIds() {
        registerTemple();
        fixture.service.clearAll();

        assertTrue(fixture.service.getStructureIds().isEmpty(), "clearAll must leave no structure ids");
    }

    @Test
    void clearAllResetsRegistrationCount() {
        registerTemple();
        fixture.service.clearAll();

        assertEquals(0, fixture.service.getRegistrations().get("structures"),
            "clearAll must reset the registration count to zero");
    }
}
