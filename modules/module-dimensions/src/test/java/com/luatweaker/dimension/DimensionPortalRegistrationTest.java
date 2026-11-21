package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies portal registration, portal target lookup and their validation. */
class DimensionPortalRegistrationTest extends DimensionServiceTestBase {

    private static final String PORTAL_BLOCK = "arcane:crystal_portal";
    private static final String TARGET_DIMENSION = "arcane:crystal_realm";

    @Test
    void portalRegistrationStoresMapping() {
        service.registerPortal(PORTAL_BLOCK, TARGET_DIMENSION);

        assertEquals(Map.of(PORTAL_BLOCK, TARGET_DIMENSION), service.getPortals(),
                "registering a portal must store its block -> dimension mapping");
    }

    @Test
    void portalRegistrationRejectsInvalidIds() {
        List<Executable> invalidCalls = List.of(
                () -> service.registerPortal("", TARGET_DIMENSION),
                () -> service.registerPortal(PORTAL_BLOCK, ""));
        for (Executable call : invalidCalls) {
            assertThrows(IllegalArgumentException.class, call,
                    "portal block and target ids must both be namespaced");
        }
    }

    @Test
    void portalTargetReturnsRegisteredTarget() {
        service.registerPortal(PORTAL_BLOCK, TARGET_DIMENSION);

        assertEquals(TARGET_DIMENSION, service.getPortalTarget(PORTAL_BLOCK),
                "a registered portal block must resolve to its target dimension");
    }

    @Test
    void portalTargetIsNullForUnknownBlock() {
        assertNull(service.getPortalTarget("arcane:unknown_block"),
                "an unregistered portal block must resolve to null");
    }

    @Test
    void portalTargetRejectsInvalidBlockId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getPortalTarget("Bad Id!"),
                "an invalid portal block id must be rejected");
    }

    @Test
    void portalIdsAreSorted() {
        service.create("zeta:realm", engine.createTable());
        service.create("alpha:realm", engine.createTable());

        assertEquals(List.of("alpha:realm", "zeta:realm"), service.getDimensionIds(),
                "dimension ids must be returned in sorted order");
    }
}
