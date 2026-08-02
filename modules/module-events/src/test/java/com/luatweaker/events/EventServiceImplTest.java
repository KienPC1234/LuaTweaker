package com.luatweaker.events;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the shared event bus semantics: listeners registered by a newer engine
 * REPLACE the older engine's listener, so a dispatch from the startup engine's
 * Events table (item handlers are pinned there) reaches the current runtime engine.
 */
public class EventServiceImplTest {

    @Test
    public void latestListenerReplacesOlderOne() {
        ILuaEngine startup = new CobaltLuaEngine();
        ILuaEngine runtime = new CobaltLuaEngine();
        EventLuaBinding.registerBindings(startup);
        EventLuaBinding.registerBindings(runtime);

        // Mimic mod loading: startup engine listens, then the reload engine re-listens.
        startup.executeString(
            "Events:Listen('MagicStaffUsed', function(payload) _G._handler = 'startup' end)",
            "startup_listen"
        );
        runtime.executeString(
            "Events:Listen('MagicStaffUsed', function(payload) _G._handler = 'runtime' end)",
            "runtime_listen"
        );

        // Dispatch from the STARTUP engine's Events table (item handler path).
        ILuaTable payload = startup.createTable();
        payload.rawset("x", startup.wrapNumber(1));
        startup.executeString(
            "Events:Fire('MagicStaffUsed', { x = 1 })",
            "startup_fire"
        );

        ILuaValue handler = runtime.getGlobalEnvironment().rawget("_handler");
        assertNotNull(handler, "the runtime listener must have been invoked");
        assertEquals("runtime", handler.asString(),
                "the most recently loaded engine's listener must win");
    }

    @Test
    public void multipleEventsStayIndependent() {
        ILuaEngine engine = new CobaltLuaEngine();
        EventLuaBinding.registerBindings(engine);

        engine.executeString(
            "Events:Listen('a', function() _G._a = 1 end)\n" +
            "Events:Listen('b', function() _G._b = 2 end)\n",
            "listen_both"
        );
        engine.executeString("Events:Fire('b', {})", "fire_b");

        assertNotNull(engine.getGlobalEnvironment().rawget("_b"));
        ILuaValue aVal = engine.getGlobalEnvironment().rawget("_a");
        assertTrue(aVal == null || aVal.isNil(),
                "firing 'b' must not invoke the 'a' listener");
    }
}
