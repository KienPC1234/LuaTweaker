package com.luatweaker.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the engine-limit config surface: every cap must ship disabled
 * so an upgrade never changes VM behaviour behind the player's back.
 */
class LuaTweakerConfigTest {

    @Test
    void defaultDataIsPopulated() {
        assertAll("default config properties",
                () -> assertTrue(LuaTweakerConfig.isAutoGenerateStubs(),
                        "stubs generation enabled by default"),
                () -> assertTrue(LuaTweakerConfig.isSuppressExperimentalWarning(),
                        "suppress warnings by default"),
                () -> assertEquals("kick", LuaTweakerConfig.getModSyncMode(),
                        "mod sync mode default is kick"));
    }

    @Test
    void engineLimitsShipDisabled() {
        assertAll("default caps",
                () -> assertEquals(0L, LuaTweakerConfig.getMaxMemoryBytes(),
                        "the memory cap must ship disabled so upgrades never change VM behaviour"),
                () -> assertEquals(0, LuaTweakerConfig.getMaxCallDepth(),
                        "the call-depth cap must ship disabled so upgrades never change VM behaviour"),
                () -> assertEquals(0, LuaTweakerConfig.getMaxCoroutines(),
                        "the coroutine cap must ship disabled so upgrades never change VM behaviour"));
    }
}
