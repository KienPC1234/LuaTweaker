package com.luatweaker.common.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.events.EventLuaBinding;
import com.luatweaker.events.EventServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies cancellable game event dispatch, alias cancellation, and lazy payload skipping. */
class EventDispatcherCancellationTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        EventServiceImpl.fireTeardownHooks();
        LuaServiceRegistry.clear();
        engine = LuaEngineFactory.create(true);
        EventLuaBinding.registerBindings(engine);
        LuaTweakerCommon.setActiveEngine(engine);
    }

    @AfterEach
    void tearDown() {
        EventServiceImpl.fireTeardownHooks();
        LuaServiceRegistry.clear();
        if (engine != null) {
            engine.shutdown();
        }
        LuaTweakerCommon.setActiveEngine(null);
    }

    @Test
    void allowsEventWhenListenerReturnsNil() {
        engine.executeStringOrThrow("""
                local Events = require("LuaTweaker.Events")
                Events:Listen("BlockBreak", function(e)
                    local seen = e
                end)
                """, "allow_listener.lua");

        boolean allowed = EventDispatcher.postCancellable("BlockBreak", engine::createTable);

        assertTrue(allowed, "a Lua listener returning nil must allow the game event to proceed");
    }

    @Test
    void cancelsEventWhenPrimaryListenerReturnsFalse() {
        engine.executeStringOrThrow("""
                local Events = require("LuaTweaker.Events")
                Events:Listen("BlockBreak", function(e)
                    return false
                end)
                """, "cancel_primary.lua");

        boolean allowed = EventDispatcher.postCancellable("BlockBreak", engine::createTable);

        assertFalse(allowed, "a Lua listener returning false on the primary event must cancel it");
    }

    @Test
    void cancelsEventWhenLegacyAliasListenerReturnsFalse() {
        engine.executeStringOrThrow("""
                local Events = require("LuaTweaker.Events")
                Events:Listen("EntityHurt", function(e)
                    return false
                end)
                """, "cancel_alias.lua");

        boolean allowed = EventDispatcher.postCancellable("LivingHurt", engine::createTable);

        assertFalse(allowed, "a Lua listener returning false on a legacy alias must cancel the event");
    }

    @Test
    void postLazySkipsPayloadConstructionWithoutListeners() {
        AtomicBoolean constructed = new AtomicBoolean(false);

        EventDispatcher.postLazy("LivingHurt", () -> {
            constructed.set(true);
            return engine.createTable();
        });

        assertFalse(constructed.get(),
                "postLazy must not allocate payload tables when no listener is registered");
    }
}
