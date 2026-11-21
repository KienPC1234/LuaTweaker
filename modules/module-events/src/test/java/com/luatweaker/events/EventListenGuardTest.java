package com.luatweaker.events;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that only Lua functions count as listeners and that the hot-path
 * guard sees listeners registered from Lua.
 */
class EventListenGuardTest {

    private static final String BAD_CALLBACK_EVENT = "BadCallbackEventProbe";
    private static final String LISTENED_EVENT = "TickOptProbeEvent";
    private static final String UNLISTENED_EVENT = "TickOptUnlistenedEvent";

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
        EventLuaBinding.registerBindings(engine);
    }

    @Test
    void rejectedCallbackDoesNotCancelEvents() {
        service.listen(BAD_CALLBACK_EVENT, "not a function");

        assertTrue(service.fireCancellable(BAD_CALLBACK_EVENT, engine.createTable()),
                "a non-function callback must never cancel an event");
    }

    @Test
    void rejectedCallbackDoesNotCountAsListener() {
        service.listen(BAD_CALLBACK_EVENT, "not a function");

        assertFalse(service.hasListeners(BAD_CALLBACK_EVENT),
                "a rejected callback must not count as a listener");
    }

    @Test
    void hasListenersIsFalseBeforeAnyListener() {
        assertFalse(service.hasListeners(LISTENED_EVENT),
                "a fresh event must report no listeners");
    }

    @Test
    void hasListenersIsTrueAfterLuaRegistration() {
        engine.executeString(
            "Events:Listen('" + LISTENED_EVENT + "', function() end)\n",
            "test_has_listeners");

        assertTrue(service.hasListeners(LISTENED_EVENT),
                "a listener registered from Lua must be visible to the hot-path guard");
    }

    @Test
    void hasListenersIsFalseForUnlistenedEvent() {
        assertFalse(service.hasListeners(UNLISTENED_EVENT),
                "an event nobody listens to must report no listeners");
    }
}
