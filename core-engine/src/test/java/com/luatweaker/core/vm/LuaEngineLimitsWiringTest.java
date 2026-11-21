package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Configured caps must reach the luava state, including caps forwarded by the factory. */
class LuaEngineLimitsWiringTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void appliesConfiguredMemoryCap() {
        long memory = 4L * 1024L * 1024L;
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(memory, 500, 16));
        assertEquals(memory, engine.rawState().getMaxMemoryBytes(),
                "appliesConfiguredMemoryCap: memory cap must reach the state");
    }

    @Test
    void appliesConfiguredCallDepthCap() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(4L * 1024L * 1024L, 500, 16));
        assertEquals(500, engine.rawState().getMaxCallDepth(),
                "appliesConfiguredCallDepthCap: call depth cap must reach the state");
    }

    @Test
    void appliesConfiguredCoroutineCap() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(4L * 1024L * 1024L, 500, 16));
        assertEquals(16, engine.rawState().getMaxCoroutines(),
                "appliesConfiguredCoroutineCap: coroutine cap must reach the state");
    }

    @Test
    void appliesOnlyMemoryCapWhenOthersAreUnset() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(0L, 0, 32));
        assertEquals(0L, engine.rawState().getMaxMemoryBytes(),
                "appliesOnlyMemoryCapWhenOthersAreUnset: unset memory cap must stay zero");
    }

    @Test
    void appliesOnlyCallDepthCapWhenOthersAreUnset() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(0L, 0, 32));
        assertEquals(0, engine.rawState().getMaxCallDepth(),
                "appliesOnlyCallDepthCapWhenOthersAreUnset: unset call depth cap must stay zero");
    }

    @Test
    void appliesOnlyCoroutineCapWhenOthersAreSet() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(0L, 0, 32));
        assertEquals(32, engine.rawState().getMaxCoroutines(),
                "appliesOnlyCoroutineCapWhenOthersAreSet: set coroutine cap must reach the state");
    }

    @Test
    void factoryBuildsALuavaEngine() {
        ILuaEngine engine = LuaEngineFactory.create(false, new LuaEngineLimits(0L, 321, 0));
        assertTrue(engine instanceof LuavaEngine, "factory must build a luava engine");
    }

    @Test
    void factoryForwardsLimits() {
        ILuaEngine engine = LuaEngineFactory.create(false, new LuaEngineLimits(0L, 321, 0));
        assertEquals(321, ((LuavaEngine) engine).rawState().getMaxCallDepth(),
                "factoryForwardsLimits: factory must forward the call depth cap");
    }
}
