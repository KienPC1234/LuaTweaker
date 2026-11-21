package com.luatweaker.core.vm;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A tripped cap must surface as a Lua error instead of a JVM crash, and sane scripts keep running. */
class LuaEngineLimitsEnforcementTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void callDepthCapStopsRunawayRecursion() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(0L, 200, 0));
        // Not a tail call (the +1 keeps the frame alive), so frames accumulate until the cap.
        IllegalStateException error;
        try {
            engine.executeStringOrThrow(
                    "local function dive(n) return 1 + dive(n + 1) end dive(1)", "runaway_recursion");
            throw new AssertionError("expected IllegalStateException but nothing was thrown");
        } catch (IllegalStateException expected) {
            error = expected;
        }
        assertTrue(error != null && error.getMessage().contains("stack overflow"),
                "callDepthCapStopsRunawayRecursion: the cap must trip as a Lua stack overflow");
    }

    @Test
    void callDepthCapStillAllowsNormalRecursion() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(0L, 200, 0));
        assertDoesNotThrow(
                () -> engine.executeStringOrThrow(
                        "local function sum(n) if n == 0 then return 0 end return n + sum(n - 1) end\n"
                                + "assert(sum(100) == 5050, 'sum(100) was ' .. tostring(sum(100)))",
                        "normal_recursion"),
                "callDepthCapStillAllowsNormalRecursion: bounded recursion must keep working");
    }

    @Test
    void coroutineCapStopsCoroutineLeak() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(0L, 0, 2));
        IllegalStateException error;
        try {
            engine.executeStringOrThrow(
                    "coroutine.create(function() end)\n"
                            + "coroutine.create(function() end)\n"
                            + "coroutine.create(function() end)",
                    "coroutine_bomb");
            throw new AssertionError("expected IllegalStateException but nothing was thrown");
        } catch (IllegalStateException expected) {
            error = expected;
        }
        assertTrue(error != null && error.getMessage().contains("coroutine limit exceeded"),
                "coroutineCapStopsCoroutineLeak: the cap must name the coroutine limit");
    }

    @Test
    void coroutineCapAllowsSequentialCoroutines() {
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(0L, 0, 2));
        // Finished coroutines release their slot, so three sequential ones are fine.
        assertDoesNotThrow(
                () -> engine.executeStringOrThrow(
                        "for i = 1, 3 do\n"
                                + "  local co = coroutine.create(function(v) return v end)\n"
                                + "  local ok, v = coroutine.resume(co, i)\n"
                                + "  assert(ok and v == i, 'coroutine ' .. i .. ' returned ' .. tostring(v))\n"
                                + "end",
                        "sequential_coroutines"),
                "coroutineCapAllowsSequentialCoroutines: released coroutine slots must be reusable");
    }

    @Test
    void memoryCapStopsRunawayAllocation() {
        long oneMebibyte = 1024L * 1024L;
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(oneMebibyte, 0, 0));
        IllegalStateException error;
        try {
            engine.executeStringOrThrow(
                    "local chunks = {}\n"
                            + "for i = 1, 64 do chunks[i] = string.rep('x', 1024 * 1024) end",
                    "memory_bomb");
            throw new AssertionError("expected IllegalStateException but nothing was thrown");
        } catch (IllegalStateException expected) {
            error = expected;
        }
        assertTrue(error != null && error.getMessage().contains("memory limit exceeded"),
                "memoryCapStopsRunawayAllocation: the cap must name the memory limit");
    }

    @Test
    void memoryCapStillAllowsSmallScripts() {
        long oneMebibyte = 1024L * 1024L;
        LuavaEngine engine = new LuavaEngine(false, new LuaEngineLimits(oneMebibyte, 0, 0));
        assertDoesNotThrow(
                () -> engine.executeStringOrThrow(
                        "local t = {}\n"
                                + "for i = 1, 1000 do t[i] = i end\n"
                                + "assert(#t == 1000, 'expected 1000 entries, got ' .. tostring(#t))",
                        "small_allocation"),
                "memoryCapStillAllowsSmallScripts: small allocations must keep working");
    }
}
