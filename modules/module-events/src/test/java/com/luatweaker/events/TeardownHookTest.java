package com.luatweaker.events;

import com.luatweaker.api.event.EventNames;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeardownHookTest {

    private static final String LISTEN_UNLOAD =
            "Events:Listen('OnScriptUnload', function()\n";
    private static final String END_LISTEN = "end)";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        EventServiceImpl.fireTeardownHooks();
    }

    @Test
    void onScriptUnloadIsCalledOnTeardown() {
        ILuaEngine engine = runScript(
            "_G._teardownCalled = false\n"
            + LISTEN_UNLOAD
            + "    _G._teardownCalled = true\n"
            + END_LISTEN);

        EventServiceImpl.fireTeardownHooks();

        ILuaValue result = engine.getGlobalEnvironment().rawget("_teardownCalled");
        assertTrue(result != null && result.asBoolean(),
                "the OnScriptUnload callback must have been called during teardown");
    }

    @Test
    void onScriptUnloadListenerRunsOncePerTeardown() {
        ILuaEngine engine = runScript(
            "_G._callCount = 0\n"
            + LISTEN_UNLOAD
            + "    _G._callCount = _G._callCount + 1\n"
            + END_LISTEN);

        EventServiceImpl.fireTeardownHooks();

        assertEquals(1, engine.getGlobalEnvironment().rawget("_callCount").asInt(),
                "the teardown callback must run exactly once");
    }

    @Test
    void teardownClearsListenersSoTheyDoNotRunAgain() {
        ILuaEngine engine = runScript(
            "_G._callCount = 0\n"
            + LISTEN_UNLOAD
            + "    _G._callCount = _G._callCount + 1\n"
            + END_LISTEN);
        EventServiceImpl.fireTeardownHooks();
        engine.executeString("_G._callCount = 0", "reset_counter");

        EventServiceImpl.fireTeardownHooks();

        assertEquals(0, engine.getGlobalEnvironment().rawget("_callCount").asInt(),
                "a second teardown must not re-run the already cleared listener");
    }

    @Test
    void onlyTheLastTeardownListenerIsKept() {
        ILuaEngine engine = runScript(
            "_G._whichCallback = 'none'\n"
            + LISTEN_UNLOAD
            + "    _G._whichCallback = 'first'\n"
            + END_LISTEN
            + "\n"
            + LISTEN_UNLOAD
            + "    _G._whichCallback = 'second'\n"
            + END_LISTEN);

        EventServiceImpl.fireTeardownHooks();

        assertEquals("second", engine.getGlobalEnvironment().rawget("_whichCallback").asString(),
                "Listen must replace the previous teardown listener (replace semantics)");
    }

    @Test
    void teardownWithoutListenersDoesNotThrow() {
        assertDoesNotThrow(EventServiceImpl::fireTeardownHooks,
                "teardown with no listeners must be a no-op instead of an error");
    }

    @Test
    void eventNameConstantPointAtOnScriptUnload() {
        assertEquals("OnScriptUnload", EventNames.ON_SCRIPT_UNLOAD,
                "EventNames.ON_SCRIPT_UNLOAD must name the teardown event");
    }

    private static ILuaEngine runScript(String script) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        EventLuaBinding.registerBindings(engine);
        engine.executeString(script, "register_teardown");
        return engine;
    }
}
