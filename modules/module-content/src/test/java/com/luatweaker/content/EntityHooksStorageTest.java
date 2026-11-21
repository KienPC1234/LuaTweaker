package com.luatweaker.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Lua lifecycle handlers are stored on the builder. */
class EntityHooksStorageTest extends EntityHookFixture {

    @Test
    void tickHandlerStored() {
        assertNotNull(hooked().getOnTickHandler(), "the tick handler must be stored");
    }

    @Test
    void hurtHandlerStored() {
        assertNotNull(hooked().getOnHurtHandler(), "the hurt handler must be stored");
    }

    @Test
    void deathHandlerStored() {
        assertNotNull(hooked().getOnDeathHandler(), "the death handler must be stored");
    }

    @Test
    void interactHandlerStored() {
        assertNotNull(hooked().getOnInteractHandler(), "the interact handler must be stored");
    }

    @Test
    void tickRateKept() {
        assertEquals(2, hooked().getTickRate(), "the tick rate must be kept");
    }
}
