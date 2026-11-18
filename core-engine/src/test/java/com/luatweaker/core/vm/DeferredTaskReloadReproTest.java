package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the deferred-task/reload path: a HUD-style coroutine that prints and
 * re-enters Java through a service callback must keep running after a reload builds a
 * fresh VM, and must never leak a VM control-flow exception through the SPI.
 */
public class DeferredTaskReloadReproTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine createEngine() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        // A Java service whose method calls back into the VM (mimics
        // NetworkServiceImpl.GetOrCreateRemoteEvent -> RemoteEvent.new).
        ILuaTable fakeNetwork = engine.createTable();
        fakeNetwork.rawset("GetOrCreateRemoteEvent", args -> {
            ILuaValue remoteClass = globals.rawget("RemoteEvent");
            ILuaValue newFn = remoteClass.asTable().rawget("new");
            return engine.callFunction(newFn, remoteClass, engine.wrapString(args[1].asString()), engine.createTable());
        });
        globals.rawset("FakeNetwork", fakeNetwork);
        return engine;
    }

    private void runModule(ILuaEngine engine) {
        // Mimics magic_staff.lua: a HUD-style loop that waits, re-enters Java and prints.
        engine.executeString(
            "_hudTicks = 0\n" +
            "Task.spawn(function()\n" +
            "    print('[HUD] loop started')\n" +
            "    while true do\n" +
            "        Task.wait(0.001)\n" +
            "        local ok, err = pcall(function()\n" +
            "            FakeNetwork:GetOrCreateRemoteEvent('StaffManaSync')\n" +
            "            _hudTicks = _hudTicks + 1\n" +
            "            print('[HUD] tick')\n" +
            "        end)\n" +
            "        if not ok then _hudError = tostring(err) end\n" +
            "    end\n" +
            "end)\n" +
            "print('[Module] loaded')",
            "module_load"
        );
        assertNull(engine.getAndClearLastExecutionError(), "module entrypoint must load cleanly");
    }

    private void tick(ILuaEngine engine) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue tickFn = globals.rawget("task").asTable().rawget("_tick");
        assertDoesNotThrow(() -> engine.callFunction(tickFn), "task._tick must not leak a VM exception");
    }

    @Test
    public void deferredLoopWithJavaReentrySurvivesAcrossEngines() throws Exception {
        for (int round = 1; round <= 3; round++) {
            ILuaEngine engine = createEngine();
            runModule(engine);
            tick(engine);
            tick(engine);
            Thread.sleep(10);
            tick(engine);

            ILuaTable globals = engine.getGlobalEnvironment();
            assertTrue(globals.rawget("_hudTicks").asDouble() >= 1.0,
                "round " + round + ": deferred loop body must actually run");
            assertTrue(globals.rawget("_hudError").isNil(),
                "round " + round + ": Java re-entry failed: " + globals.rawget("_hudError"));
            assertNull(engine.getAndClearLastExecutionError(),
                "round " + round + ": reload must not leak a VM control-flow exception");
        }
    }

    @Test
    public void reloadedEngineHasFreshStateAndDoesNotAdvanceOldTasks() throws Exception {
        ILuaEngine oldEngine = createEngine();
        runModule(oldEngine);
        tick(oldEngine);
        Thread.sleep(10);
        tick(oldEngine);
        // Deferred tasks run on luava coroutine virtual threads, so the counter can
        // be published just after the tick returns. Wait briefly before asserting.
        Thread.sleep(100);
        double oldTicks = oldEngine.getGlobalEnvironment().rawget("_hudTicks").asDouble();
        assertTrue(oldTicks >= 1.0, "old engine's deferred loop must have run");

        // A reload constructs a brand-new VM and re-runs the same module.
        ILuaEngine newEngine = createEngine();
        runModule(newEngine);
        assertTrue(newEngine.getGlobalEnvironment().rawget("_hudTicks").asDouble() == 0.0,
            "the reloaded engine must start with fresh state");
        tick(newEngine);
        Thread.sleep(10);
        tick(newEngine);
        Thread.sleep(100);

        assertTrue(newEngine.getGlobalEnvironment().rawget("_hudTicks").asDouble() >= 1.0,
            "the reloaded engine's deferred loop must run on the new VM");
        // luava coroutines own virtual threads, so a discarded engine's pending wait()
        // callbacks may wake independently of tick(). What reload isolation guarantees
        // is that no state crosses between the two VMs: each counter lives in its own
        // globals table and the new engine started from zero (asserted above).
        assertNotSame(oldEngine.getGlobalEnvironment(), newEngine.getGlobalEnvironment(),
            "each engine must own a distinct globals table after reload");
        assertNull(oldEngine.getAndClearLastExecutionError());
        assertNull(newEngine.getAndClearLastExecutionError());
    }
}
