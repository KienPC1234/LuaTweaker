package com.luatweaker.common.server;

import com.luatweaker.common.interaction.WorldBorderOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link ServerServiceImpl} and {@link WorldBorderOps} safe guards when no server
 * is active or callers pass out-of-range border/spawn arguments.
 */
class ServerOpsAndBorderTest {

    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void serverServiceReturnsEmptyAndNullWhenNoServerIsRunning() {
        ServerServiceImpl service = new ServerServiceImpl();
        service.setWorldBorder(0.0, 0.0, 100.0, 0L, OVERWORLD);

        assertAll("ServerServiceImpl without active server",
                () -> assertTrue(service.getPlayers().isEmpty(), "getPlayers must return empty list"),
                () -> assertNull(service.getWorldBorder(OVERWORLD), "getWorldBorder must return null"));
    }

    @Test
    void worldBorderOpsRejectsInvalidParametersAndMissingServer() {
        assertAll("WorldBorderOps validation without server",
                () -> assertNull(WorldBorderOps.getWorldSpawn(OVERWORLD), "getWorldSpawn must return null"),
                () -> assertFalse(WorldBorderOps.applyWorldSpawn(OVERWORLD, 0, 64, 0, Float.NaN),
                        "applyWorldSpawn with NaN angle must fail"),
                () -> assertFalse(WorldBorderOps.applyWorldSpawn(OVERWORLD, 0, 64, 0, 0.0f),
                        "applyWorldSpawn without server must fail"),
                () -> assertNull(WorldBorderOps.getWorldBorder(OVERWORLD), "getWorldBorder must return null"),
                () -> assertFalse(WorldBorderOps.applyWorldBorder(OVERWORLD, Double.NaN, 0.0, 100.0, 0L),
                        "applyWorldBorder with NaN center must fail"),
                () -> assertFalse(WorldBorderOps.applyWorldBorder(OVERWORLD, 0.0, 0.0, 0.5, 0L),
                        "applyWorldBorder below minimum size 1.0 must fail"),
                () -> assertFalse(WorldBorderOps.applyWorldBorder(OVERWORLD, 0.0, 0.0, 100.0, -1L),
                        "applyWorldBorder with negative lerpMillis must fail"));
    }
}
