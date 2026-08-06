package com.luatweaker.events;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies cancellable events: listeners returning Lua `false` cancel the
 * event; other return values do not.
 */
public class EventServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine engine;
    private EventServiceImpl service;

    @BeforeEach
    void setup() {
        engine = new CobaltLuaEngine();
        service = new EventServiceImpl(engine);
    }

    @Test
    void fireCancellable_ListenerReturningFalseCancels() {
        com.luatweaker.events.EventLuaBinding.registerBindings(engine);
        engine.executeString(
            "Events:Listen('MobSpawnAttempt', function(event)\n" +
            "    if event.entityId == 'minecraft:phantom' then\n" +
            "        return false\n" +
            "    end\n" +
            "    return true\n" +
            "end)\n",
            "test_cancel_listener");

        com.luatweaker.api.vm.ILuaTable payload = engine.createTable();
        payload.rawset("entityId", "minecraft:phantom");
        assertFalse(service.fireCancellable("MobSpawnAttempt", payload),
                "phantom must be cancelled");

        com.luatweaker.api.vm.ILuaTable payload2 = engine.createTable();
        payload2.rawset("entityId", "minecraft:zombie");
        assertTrue(service.fireCancellable("MobSpawnAttempt", payload2),
                "zombie must not be cancelled");
    }

    @Test
    void fireCancellable_NoListenersReturnsTrue() {
        com.luatweaker.api.vm.ILuaTable payload = engine.createTable();
        assertTrue(service.fireCancellable("UnknownEvent", payload));
    }

    @Test
    void fireEvent_StillFiresAllListeners() {
        engine.executeString(
            "Events:Listen('TestEvent', function(event)\n" +
            "    return false\n" +
            "end)\n",
            "test_fire_listener");
        com.luatweaker.events.EventLuaBinding.registerBindings(engine);
        assertDoesNotThrow(() -> service.fireEvent("TestEvent", engine.createTable()),
                "fireEvent must ignore listener return values");
    }
}
