package com.luatweaker.events;

import com.luatweaker.api.event.EventNames;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.luatweaker.core.logger.AsyncFileLogger;

import static org.junit.jupiter.api.Assertions.*;

public class TeardownHookTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setup() {
        EventServiceImpl.fireTeardownHooks();
    }

    @Test
    void testOnScriptUnload_CalledOnTeardown() {
        ILuaEngine engine = new CobaltLuaEngine();
        EventLuaBinding.registerBindings(engine);

        engine.executeString(
            "_G._teardownCalled = false\n" +
            "Events:Listen('OnScriptUnload', function()\n" +
            "    _G._teardownCalled = true\n" +
            "end)",
            "register_teardown"
        );

        EventServiceImpl.fireTeardownHooks();

        ILuaValue result = engine.getGlobalEnvironment().rawget("_teardownCalled");
        assertNotNull(result, "teardown flag should exist");
        assertTrue(result.asBoolean(), "OnScriptUnload callback should have been called");
    }

    @Test
    void testOnScriptUnload_ListenersClearedAfterTeardown() {
        ILuaEngine engine = new CobaltLuaEngine();
        EventLuaBinding.registerBindings(engine);

        engine.executeString(
            "_G._callCount = 0\n" +
            "Events:Listen('OnScriptUnload', function()\n" +
            "    _G._callCount = _G._callCount + 1\n" +
            "end)",
            "register_teardown"
        );

        EventServiceImpl.fireTeardownHooks();

        ILuaValue firstCall = engine.getGlobalEnvironment().rawget("_callCount");
        assertEquals(1, firstCall.asInt(), "Should be called once");

        engine.executeString("_G._callCount = 0", "reset_counter");
        EventServiceImpl.fireTeardownHooks();

        ILuaValue secondCall = engine.getGlobalEnvironment().rawget("_callCount");
        assertEquals(0, secondCall.asInt(), "Should NOT be called again (listeners cleared)");
    }

    @Test
    void testOnScriptUnload_ReplaceSemantics() {
        ILuaEngine engine = new CobaltLuaEngine();
        EventLuaBinding.registerBindings(engine);

        engine.executeString(
            "_G._whichCallback = 'none'\n" +
            "Events:Listen('OnScriptUnload', function()\n" +
            "    _G._whichCallback = 'first'\n" +
            "end)\n" +
            "Events:Listen('OnScriptUnload', function()\n" +
            "    _G._whichCallback = 'second'\n" +
            "end)",
            "register_two"
        );

        EventServiceImpl.fireTeardownHooks();

        ILuaValue result = engine.getGlobalEnvironment().rawget("_whichCallback");
        assertEquals("second", result.asString(), "Only the last listener should be called (replace semantics)");
    }

    @Test
    void testOnScriptUnload_NoListenerDoesNotCrash() {
        assertDoesNotThrow(() -> EventServiceImpl.fireTeardownHooks(),
                "Teardown with no listeners should not throw");
    }

    @Test
    void testEventNames_Constant() {
        assertEquals("OnScriptUnload", EventNames.ON_SCRIPT_UNLOAD);
    }
}
