package com.luatweaker.content;

import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.entity.IEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Stored Lua hooks run with combat data and their returns apply. */
class EntityHookInvocationTest extends EntityHookFixture {

    private static final String HOOKED = "luatweaker:test_hooked";

    @Test
    void tickCallbackFires() {
        IEntityBuilder builder = hooked();
        IEntity mock = mockEntity(HOOKED);
        builder.getOnTickHandler().handle(mock);

        assertDoesNotThrow(() -> engine.executeStringOrThrow(
                "assert(ticked == 1, 'onTick must run the Lua callback')", "TickFiredTest"),
                "onTick must run the Lua callback");
    }

    @Test
    void hurtReturnNonNull() {
        IEntityBuilder builder = hooked();

        Float reduced = builder.getOnHurtHandler().handle(mockEntity(HOOKED), 10.0f, null);
        assertNotNull(reduced, "a numeric Lua return must replace the damage");
    }

    @Test
    void hurtHalvesDamage() {
        IEntityBuilder builder = hooked();

        Float reduced = builder.getOnHurtHandler().handle(mockEntity(HOOKED), 10.0f, null);
        assertEquals(5.0f, reduced, 0.001f, "the hurt handler must halve the damage");
    }

    @Test
    void deathCancels() {
        IEntityBuilder builder = hooked();

        assertTrue(builder.getOnDeathHandler().handle(mockEntity(HOOKED), null),
                "death hook true must cancel");
    }
}
