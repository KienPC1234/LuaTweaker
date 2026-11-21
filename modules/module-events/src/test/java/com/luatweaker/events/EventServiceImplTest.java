package com.luatweaker.events;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies cancellable events: listeners returning Lua {@code false} cancel the
 * event; other return values do not.
 */
class EventServiceImplTest {

    private static final String SPAWN_EVENT = "MobSpawnAttempt";
    private static final String PHANTOM_ID = "minecraft:phantom";
    private static final String ZOMBIE_ID = "minecraft:zombie";
    private static final String BAD_CALLBACK_EVENT = "BadCallbackEvent";

    private ILuaEngine engine;
    private EventServiceImpl service;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        service = new EventServiceImpl(engine);
    }

    @Test
    void listenerReturningFalseCancelsTheEvent() {
        registerPhantomFilter();

        assertFalse(service.fireCancellable(SPAWN_EVENT, payload(PHANTOM_ID)),
                "a listener returning false must cancel the phantom spawn");
    }

    @Test
    void listenerReturningTrueKeepsTheEvent() {
        registerPhantomFilter();

        assertTrue(service.fireCancellable(SPAWN_EVENT, payload(ZOMBIE_ID)),
                "a listener returning true must keep the zombie spawn");
    }

    @Test
    void fireCancellableWithoutListenersReturnsTrue() {
        assertTrue(service.fireCancellable("UnknownEvent", engine.createTable()),
                "an event without listeners must not be cancelled");
    }

    @Test
    void fireEventIgnoresListenerReturnValues() {
        EventLuaBinding.registerBindings(engine);
        engine.executeString(
            "Events:Listen('TestEvent', function(event)\n"
            + "    return false\n"
            + "end)\n",
            "test_fire_listener");

        assertDoesNotThrow(() -> service.fireEvent("TestEvent", engine.createTable()),
                "fireEvent must dispatch without treating a false return as cancellation");
    }

    @Test
    void listenRejectsNonFunctionCallbacks() {
        service.listen(BAD_CALLBACK_EVENT, "not a function");

        assertDoesNotThrow(() -> service.fireEvent(BAD_CALLBACK_EVENT, engine.createTable()),
                "a non-function callback must never be invoked by fireEvent");
    }

    private void registerPhantomFilter() {
        EventLuaBinding.registerBindings(engine);
        engine.executeString(
            "Events:Listen('" + SPAWN_EVENT + "', function(event)\n"
            + "    if event.entityId == '" + PHANTOM_ID + "' then\n"
            + "        return false\n"
            + "    end\n"
            + "    return true\n"
            + "end)\n",
            "test_cancel_listener");
    }

    private ILuaTable payload(String entityId) {
        ILuaTable table = engine.createTable();
        table.rawset("entityId", entityId);
        return table;
    }
}
