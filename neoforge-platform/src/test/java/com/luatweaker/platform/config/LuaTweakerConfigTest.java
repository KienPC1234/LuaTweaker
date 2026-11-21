package com.luatweaker.platform.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the engine-limit config surface: the STARTUP spec must stay populated (its class
 * initialiser is what registers the defaults) and every cap must ship disabled so an upgrade
 * never changes VM behaviour behind the player's back.
 */
class LuaTweakerConfigTest {

    @Test
    void bothSpecsArePopulated() {
        assertAll("non-empty specs",
                () -> assertFalse(LuaTweakerConfig.COMMON_SPEC.isEmpty(),
                        "the common config spec must register its values"),
                () -> assertFalse(LuaTweakerConfig.ENGINE_SPEC.isEmpty(),
                        "the engine config spec must register its values"));
    }

    @Test
    void engineLimitsShipDisabled() {
        assertAll("default caps",
                () -> assertEquals(0L, LuaTweakerConfig.MAX_MEMORY_BYTES.getDefault(),
                        "the memory cap must ship disabled so upgrades never change VM behaviour"),
                () -> assertEquals(0, LuaTweakerConfig.MAX_CALL_DEPTH.getDefault(),
                        "the call-depth cap must ship disabled so upgrades never change VM behaviour"),
                () -> assertEquals(0, LuaTweakerConfig.MAX_COROUTINES.getDefault(),
                        "the coroutine cap must ship disabled so upgrades never change VM behaviour"));
    }
}
