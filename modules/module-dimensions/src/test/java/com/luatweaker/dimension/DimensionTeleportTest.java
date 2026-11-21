package com.luatweaker.dimension;

import com.luatweaker.api.pal.Platform;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies direct {@code teleportTo} delegation and its failure paths. */
class DimensionTeleportTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void teleportToDelegatesToPlatform() {
        AtomicReference<String> seen = new AtomicReference<>();
        AtomicReference<Object> rawSeen = new AtomicReference<>();
        Platform.setDimension(dimensionPlatform(true, seen, null, rawSeen));
        service.create(DIMENSION_ID, engine.createTable());

        service.teleportTo(playerWithRaw("mock-raw-player"), DIMENSION_ID);

        assertEquals(List.of(DIMENSION_ID, "mock-raw-player"),
                List.of(seen.get(), rawSeen.get()),
                "teleportTo must pass the dimension id and raw entity to the platform");
    }

    @Test
    void teleportToRejectsUnknownDimension() {
        assertThrows(IllegalArgumentException.class,
                () -> service.teleportTo(playerWithRaw("mock"), "minecraft:unknown"),
                "teleporting to an unregistered dimension must be rejected");
    }

    @Test
    void teleportToThrowsWhenLevelNotLoaded() {
        Platform.setDimension(dimensionPlatform(false, null, null, null));
        service.create(DIMENSION_ID, engine.createTable());

        assertThrows(IllegalStateException.class,
                () -> service.teleportTo(playerWithRaw("mock"), DIMENSION_ID),
                "teleporting to a dimension whose level did not load must fail loudly");
    }
}
