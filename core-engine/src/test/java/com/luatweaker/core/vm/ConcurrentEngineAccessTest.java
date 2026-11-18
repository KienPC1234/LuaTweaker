package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The chosen thread model is a single shared VM whose entry points are synchronized.
 * Two threads (a "render" thread running scripts and a "server" thread invoking Lua
 * functions) must therefore serialize cleanly: no crashes and no lost updates.
 */
public class ConcurrentEngineAccessTest {

    private static final int ITERATIONS = 200;

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    public void concurrentExecuteStringAndCallFunctionDoNotCorruptTheVM() throws Exception {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeString(
            "_concurrentCounter = 0\n" +
            "function _concurrentIncrement(amount)\n" +
            "    _concurrentCounter = _concurrentCounter + amount\n" +
            "    return _concurrentCounter\n" +
            "end",
            "concurrent_setup"
        );
        assertNull(engine.getAndClearLastExecutionError(), "setup script must compile and run cleanly");

        ILuaValue incrementFn = globals.rawget("_concurrentIncrement");
        assertTrue(incrementFn.isFunction(), "increment function must be registered");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);

        Thread scriptThread = new Thread(() -> {
            try {
                for (int i = 1; i <= ITERATIONS; i++) {
                    engine.executeString(
                        "_concurrentFromJava = " + i + "\n" +
                        "assert(type(_concurrentIncrement) == 'function', 'setup must remain visible')",
                        "concurrent_write_" + i
                    );
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "script-thread");

        Thread functionThread = new Thread(() -> {
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    ILuaValue[] results = engine.callFunctionMulti(incrementFn, engine.wrapNumber(1));
                    if (results.length != 1 || results[0].asInt() != i + 1) {
                        failure.compareAndSet(null, new AssertionError(
                            "unexpected increment result at iteration " + i + ": "
                                + (results.length == 0 ? "<none>" : results[0].asString())));
                        break;
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "function-thread");

        scriptThread.start();
        functionThread.start();
        assertTrue(done.await(30, TimeUnit.SECONDS), "both threads must finish");

        Throwable t = failure.get();
        assertNull(t, "concurrent VM access must not throw: " + (t == null ? "" : t));
        assertEquals((double) ITERATIONS, globals.rawget("_concurrentFromJava").asDouble(),
            "all script writes must be visible");
        assertEquals((double) ITERATIONS, globals.rawget("_concurrentCounter").asDouble(),
            "all function-call increments must be applied");
        assertNull(engine.getAndClearLastExecutionError(), "no script error may be recorded");
    }

    @Test
    public void concurrentCallFunctionFromTwoThreadsPreservesEveryIncrement() throws Exception {
        ILuaEngine engine = LuaEngineFactory.create(false);
        engine.executeString(
            "_sharedCounter = 0\n" +
            "function _incrementShared() _sharedCounter = _sharedCounter + 1 return _sharedCounter end",
            "concurrent_setup_shared"
        );
        assertNull(engine.getAndClearLastExecutionError());

        ILuaValue incrementFn = engine.getGlobalEnvironment().rawget("_incrementShared");
        assertTrue(incrementFn.isFunction());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);
        Runnable incrementLoop = () -> {
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    engine.callFunction(incrementFn);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        };

        Thread first = new Thread(incrementLoop, "first-thread");
        Thread second = new Thread(incrementLoop, "second-thread");
        first.start();
        second.start();
        assertTrue(done.await(30, TimeUnit.SECONDS), "both threads must finish");

        Throwable t = failure.get();
        assertNull(t, "concurrent callFunction must not throw: " + (t == null ? "" : t));
        assertEquals((double) (2 * ITERATIONS),
            engine.getGlobalEnvironment().rawget("_sharedCounter").asDouble(),
            "synchronized entry points must not lose increments");
    }
}
