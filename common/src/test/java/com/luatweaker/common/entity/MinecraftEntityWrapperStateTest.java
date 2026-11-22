package com.luatweaker.common.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the null-safe edge of the new movement/game-mode primitives. */
class MinecraftEntityWrapperStateTest {

    @Test
    void nullEntityReportsZeroMovementAndEmptySnapshot() {
        assertNullEntity(new MinecraftEntityWrapper(null));
    }

    @Test
    void nullPlayerStillReportsASafeGameModeAndNoPing() {
        assertNullPlayer(new MinecraftPlayerWrapper(null));
    }

    private static void assertNullEntity(MinecraftEntityWrapper wrapper) {
        assertZeroMovement(wrapper);
        assertTrue(wrapper.getMovementData().isEmpty(),
                "a null entity must produce an empty movement snapshot");
        assertFalse(wrapper.isFlying(), "a null entity cannot be flying");
    }

    private static void assertNullPlayer(MinecraftPlayerWrapper player) {
        assertEquals("SURVIVAL", player.getGameMode(), "a null player must default to SURVIVAL");
        assertEquals(0, player.getPing(), "a null player has no connection latency");
    }

    private static void assertZeroMovement(MinecraftEntityWrapper wrapper) {
        assertEquals(0.0, wrapper.getDeltaX(), "delta X must be 0");
        assertEquals(0.0, wrapper.getDeltaY(), "delta Y must be 0");
        assertEquals(0.0, wrapper.getDeltaZ(), "delta Z must be 0");
        assertEquals(0.0, wrapper.getHorizontalSpeed(), "horizontal speed must be 0");
        assertEquals(0.0, wrapper.getSpeed(), "speed must be 0");
        assertEquals(0.0F, wrapper.getFallDistance(), "fall distance must be 0");
    }

    @Test
    void nullEntityReportsSafeRotationAndStatePrimitives() {
        MinecraftEntityWrapper wrapper = new MinecraftEntityWrapper(null);

        Assertions.assertAll("rotation/state primitives must be null-safe",
                () -> assertEquals(0.0F, wrapper.getPreviousYaw(), "previous yaw must default to 0"),
                () -> assertEquals(0.0F, wrapper.getPreviousPitch(), "previous pitch must default to 0"),
                () -> assertEquals(0.0F, wrapper.getYawDelta(), "yaw delta must default to 0"),
                () -> assertEquals(0.0F, wrapper.getPitchDelta(), "pitch delta must default to 0"),
                () -> assertEquals(0.0F, wrapper.getHeadYaw(), "head yaw must default to 0"),
                () -> assertEquals(0, wrapper.getSwingTime(), "swing time must default to 0"),
                () -> assertEquals(0.0, wrapper.getEntityInteractionRange(), "reach must default to 0"),
                () -> assertFalse(wrapper.isSwimming(), "a null entity cannot be swimming"),
                () -> assertFalse(wrapper.isSwinging(), "a null entity cannot be swinging"),
                () -> assertFalse(wrapper.isNoAi(), "a null entity has no AI toggle"),
                () -> assertFalse(wrapper.isInvisible(), "a null entity is not invisible"),
                () -> assertFalse(wrapper.isInvulnerable(), "a null entity is not invulnerable"));
    }
}
