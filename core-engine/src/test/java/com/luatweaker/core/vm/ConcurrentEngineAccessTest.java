package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: the engine LuaState must not be corrupted by concurrent access from
 * a "render thread" (OnRenderHUD fire) and a "server thread" (task._tick + module
 * loading), which is what caused the production ClassCastException after a reload.
 */
public class ConcurrentEngineAccessTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    public void concurrentRenderAndTickDoNotCorruptTheVM() throws Exception {
        ILuaEngine engine = new CobaltLuaEngine();
        ILuaTable globals = engine.getGlobalEnvironment();

        // Setup: HUD-style signal + deferred loop, mimicking magic_staff_client/server
        engine.executeString(
            "_reproHudSignal = Signal.new()\n" +
            "_reproHudSignal:Connect(function(dt)\n" +
            "    print('[HUD] render frame ' .. tostring(dt))\n" +
            "end)\n" +
            "Task.spawn(function()\n" +
            "    while true do\n" +
            "        Task.wait(0.001)\n" +
            "        print('[TICK] server tick')\n" +
            "    end\n" +
            "end)",
            "concurrent_setup"
        );

        ILuaValue signal = globals.rawget("_reproHudSignal");
        ILuaValue signalClass = globals.rawget("Signal");
        ILuaValue fireFn = signalClass.asTable().rawget("Fire");
        ILuaValue tickFn = globals.rawget("task").asTable().rawget("_tick");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);

        Thread renderThread = new Thread(() -> {
            try {
                for (int i = 0; i < 300 && failure.get() == null; i++) {
                    engine.callFunction(fireFn, signal, engine.wrapNumber(i));
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "render-thread");

        Thread serverThread = new Thread(() -> {
            try {
                for (int i = 0; i < 300 && failure.get() == null; i++) {
                    engine.callFunction(tickFn);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "server-thread");

        renderThread.start();
        serverThread.start();
        done.await();

        Throwable t = failure.get();
        assertNull(t, "Concurrent VM access must not throw (ClassCastException etc.): " + (t == null ? "" : t));
    }
}
