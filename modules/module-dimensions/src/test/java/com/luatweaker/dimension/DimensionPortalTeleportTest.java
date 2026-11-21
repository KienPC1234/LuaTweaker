package com.luatweaker.dimension;

import com.luatweaker.api.pal.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies portal use delegates to the platform teleport and handles unknown blocks. */
class DimensionPortalTeleportTest extends DimensionServiceTestBase {

    private static final String MOCK = "mock";

    private static final String PORTAL_BLOCK = "arcane:crystal_portal";
    private static final String TARGET_DIMENSION = "arcane:crystal_realm";

    private record TeleportCall(boolean used, String target, Object raw, boolean platformCalled) {
    }

    @Test
    void registeredPortalTeleportsToMappedDimension() {
        TeleportCall call = useRegisteredPortal();

        assertEquals(List.of(TARGET_DIMENSION, "mock-raw-player"), List.of(call.target(), call.raw()),
                "using a registered portal must teleport the raw player to its mapped dimension");
    }

    @Test
    void registeredPortalReturnsTrue() {
        assertTrue(useRegisteredPortal().used(),
                "using a registered portal must report success");
    }

    @Test
    void unregisteredBlockReturnsFalse() {
        assertFalse(useUnregisteredBlock().used(),
                "using an unregistered block must report that no portal was used");
    }

    @Test
    void unregisteredBlockSkipsTeleport() {
        assertFalse(useUnregisteredBlock().platformCalled(),
                "an unregistered block must not trigger a platform teleport");
    }

    @Test
    void blankBlockIdsRejected() {
        List<Executable> invalidCalls = List.of(
                () -> service.tryUsePortal(playerWithRaw(MOCK), ""),
                () -> service.tryUsePortal(playerWithRaw(MOCK), "Bad Id!"));
        for (Executable call : invalidCalls) {
            assertThrows(IllegalArgumentException.class, call,
                    "portal use with an invalid block id must be rejected");
        }
    }

    @Test
    void unknownTargetDimensionThrows() {
        Platform.setDimension(dimensionPlatform(true, null, null, null));
        service.registerPortal(PORTAL_BLOCK, "arcane:missing");

        assertThrows(IllegalArgumentException.class,
                () -> service.tryUsePortal(playerWithRaw(MOCK), PORTAL_BLOCK),
                "a portal pointing at an unknown dimension must be rejected");
    }

    private TeleportCall useRegisteredPortal() {
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<Object> rawSeen = new AtomicReference<>();
        Platform.setDimension(dimensionPlatform(true, seen, null, rawSeen));
        service.create(TARGET_DIMENSION, engine.createTable());
        service.registerPortal(PORTAL_BLOCK, TARGET_DIMENSION);
        boolean used = service.tryUsePortal(playerWithRaw("mock-raw-player"), PORTAL_BLOCK);
        return new TeleportCall(used, seen.get(), rawSeen.get(), true);
    }

    private TeleportCall useUnregisteredBlock() {
        AtomicBoolean called = new AtomicBoolean(false);
        Platform.setDimension(dimensionPlatform(true, null, called, null));
        boolean used = service.tryUsePortal(playerWithRaw(MOCK), "arcane:unknown_block");
        return new TeleportCall(used, null, null, called.get());
    }
}
