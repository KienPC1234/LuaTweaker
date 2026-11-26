package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies VM optimizations: value wrapper singletons and zero-copy varargs Signal dispatch. */
class LuavaVmOptimizationTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void nilValueReturnsSingletonInstance() {
        LuavaEngine engine = new LuavaEngine(false);
        try {
            assertSame(engine.nilValue(), engine.nilValue(),
                    "nilValue must return the shared LuavaLuaValue.NIL singleton");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void wrapBooleanTrueReturnsSingletonInstance() {
        LuavaEngine engine = new LuavaEngine(false);
        try {
            assertSame(engine.wrapBoolean(true), engine.wrapBoolean(true),
                    "wrapBoolean(true) must return the shared LuavaLuaValue.TRUE singleton");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void wrapBooleanFalseReturnsSingletonInstance() {
        LuavaEngine engine = new LuavaEngine(false);
        try {
            assertSame(engine.wrapBoolean(false), engine.wrapBoolean(false),
                    "wrapBoolean(false) must return the shared LuavaLuaValue.FALSE singleton");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void signalFireWithZeroListenersDoesNotError() {
        ILuaEngine engine = new LuavaEngine(false);
        try {
            engine.executeString("""
                    local s = Signal.new()
                    s:Fire('a', 1, true)
                    s:FireSync('b', 2, false)
                    """, "empty_signal_fast_path");
            assertNull(engine.getAndClearLastExecutionError(),
                    "firing an empty Signal must short-circuit cleanly without error");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void signalFireSyncPreservesNilHoleInVarargs() {
        ILuaEngine engine = new LuavaEngine(false);
        try {
            engine.executeStringOrThrow("""
                    local s = Signal.new()
                    s:Connect(function(a, b, c)
                        if a == 'first' and b == nil and c == 'third' then
                            _holeTag = 'ok'
                        end
                    end)
                    s:FireSync('first', nil, 'third')
                    """, "signal_nil_hole");
            assertEquals("ok", engine.getGlobalEnvironment().rawget("_holeTag").asString(),
                    "direct varargs forwarding in FireSync must preserve nil holes");
        } finally {
            engine.shutdown();
        }
    }
}
