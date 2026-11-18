package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the Signal/task pipeline:
 * 1. Signal:Fire must pass only the real arguments to listeners (no arg shift from self).
 * 2. Signal:FireSync must invoke listeners immediately on the calling thread.
 * 3. A crashing deferred listener must not stop the remaining tasks, and no VM
 *    control-flow exception may leak to Java through task._tick.
 * 4. require("LuaTweaker.GuiService" / RunService / KeyBindService) must resolve.
 */
public class SignalCallbackTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaTable createSignal(ILuaEngine engine, ILuaValue signalClass) {
        ILuaValue newSignalFn = signalClass.asTable().rawget("new");
        ILuaValue signal = engine.callFunction(newSignalFn, signalClass);
        assertNotNull(signal, "Signal.new returned nil");
        return signal.asTable();
    }

    @Test
    public void fireSignalFromJavaPassesArgsAfterSelf() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue signalClass = globals.rawget("Signal");
        assertNotNull(signalClass, "Signal class missing from bootstrap");
        ILuaTable signal = createSignal(engine, signalClass);
        globals.rawset("_reproSignal", signal);

        engine.executeString(
            "_reproSignal:Connect(function(first, second)\n" +
            "    _reproFirst = first\n" +
            "    _reproSecond = second\n" +
            "end)",
            "repro_connect"
        );

        ILuaValue fireFn = signalClass.asTable().rawget("Fire");
        ILuaTable playerTable = engine.createTable();
        playerTable.rawset("Name", engine.wrapString("Dev"));
        engine.callFunction(fireFn, signal, playerTable, engine.wrapNumber(42));
        engine.callFunction(globals.rawget("task").asTable().rawget("_tick"));

        ILuaValue first = globals.rawget("_reproFirst");
        ILuaValue second = globals.rawget("_reproSecond");
        assertNotNull(first, "listener did not receive first argument");
        assertTrue(first.isTable(), "first argument must be the player table, got: " + first.asString());
        assertEquals(42.0, second.asDouble(), "second argument must be preserved");
    }

    @Test
    public void fireSignalWithColonSyntaxFromLua() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue signalClass = globals.rawget("Signal");
        ILuaTable signal = createSignal(engine, signalClass);

        globals.rawset("_reproSignal", signal);
        engine.executeString(
            "_reproSignal:Connect(function(value)\n" +
            "    print('[Repro] listener ran, value=' .. tostring(value))\n" +
            "    _reproValue = value\n" +
            "end)\n" +
            "print('[Repro] before fire')\n" +
            "_reproSignal:Fire('hello')\n" +
            "print('[Repro] after fire')\n" +
            "require('LuaTweaker.Task')._tick()\n" +
            "print('[Repro] after tick, _reproValue=' .. tostring(_reproValue))",
            "repro_colon"
        );

        ILuaValue value = globals.rawget("_reproValue");
        assertNotNull(value, "listener did not run");
        assertEquals("hello", value.asString());
    }

    @Test
    public void fireSyncInvokesListenersImmediatelyOnCallingThread() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue signalClass = globals.rawget("Signal");
        ILuaTable signal = createSignal(engine, signalClass);
        globals.rawset("_syncSignal", signal);

        engine.executeString(
            "_syncSignal:Connect(function(value) _syncFirst = value end)\n" +
            "_syncSignal:FireSync('now')\n" +
            "assert(_syncFirst == 'now', 'FireSync must run listeners before returning')",
            "repro_firesync"
        );
        assertNull(engine.getAndClearLastExecutionError());
        assertEquals("now", globals.rawget("_syncFirst").asString());

        // Error path: a crashing listener is contained by pcall inside FireSync and
        // must not prevent listeners registered after it from running.
        engine.executeString(
            "_syncSignal:Connect(function() error('listener boom') end)\n" +
            "_syncSignal:Connect(function() _syncSurvivor = true end)\n" +
            "_syncSignal:FireSync('again')",
            "repro_firesync_error"
        );
        assertTrue(globals.rawget("_syncSurvivor").asBoolean(),
            "a healthy listener registered after a crashing one must still run under FireSync");
    }

    @Test
    public void crashingDeferredListenerDoesNotBlockRemainingTasks() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue signalClass = globals.rawget("Signal");
        ILuaTable signal = createSignal(engine, signalClass);
        globals.rawset("_reproSignal", signal);

        engine.executeString(
            "_reproSignal:Connect(function(player)\n" +
            "    player:getUuid()\n" +
            "end)\n" +
            "_reproSignal:Connect(function(player)\n" +
            "    _reproSurvived = true\n" +
            "end)",
            "repro_crash"
        );

        ILuaValue fireFn = signalClass.asTable().rawget("Fire");
        ILuaValue fakePlayer = engine.createTable();
        engine.callFunction(fireFn, signal, fakePlayer);

        ILuaValue tickFn = globals.rawget("task").asTable().rawget("_tick");
        assertDoesNotThrow(() -> engine.callFunction(tickFn),
            "task._tick must not leak a VM control-flow exception to Java when a listener crashes");

        ILuaValue survived = globals.rawget("_reproSurvived");
        assertTrue(survived != null && survived.asBoolean(),
            "the healthy listener queued after the crashing one must still run");
    }

    @Test
    public void deferredTaskWithWaitYieldsAndResumesAcrossTicks() throws Exception {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeString(
            "Task.spawn(function()\n" +
            "    _yieldStep = 'started'\n" +
            "    Task.wait(0.01)\n" +
            "    _yieldStep = 'resumed'\n" +
            "end)",
            "repro_yield"
        );

        ILuaValue tickFn = globals.rawget("task").asTable().rawget("_tick");
        engine.callFunction(tickFn);
        ILuaValue step = globals.rawget("_yieldStep");
        assertEquals("started", step.asString(), "deferred task must start and yield");

        Thread.sleep(50);
        engine.callFunction(tickFn);
        step = globals.rawget("_yieldStep");
        assertEquals("resumed", step.asString(), "task.wait must resume the coroutine on a later tick");
    }

    @Test
    public void taskDeferAndDelayQueueWorkUntilTick() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeString(
            "Task.defer(function() _deferRan = true end)\n" +
            "Task.delay(0, function() _delayRan = true end)\n" +
            "assert(_deferRan == nil, 'deferred task must not run before the tick')\n" +
            "assert(_delayRan == nil, 'delayed task must not run before the tick')",
            "repro_defer_delay"
        );
        assertNull(engine.getAndClearLastExecutionError());

        ILuaValue tickFn = globals.rawget("task").asTable().rawget("_tick");
        engine.callFunction(tickFn);
        assertTrue(globals.rawget("_deferRan").asBoolean(), "Task.defer work must run on tick");
        assertTrue(globals.rawget("_delayRan").asBoolean(), "Task.delay(0) work must run on tick");
    }

    @Test
    public void modifiedLuaScriptsAreSyntacticallyValid() throws Exception {
        String[] relativePaths = {
            "neoforge-platform/luamods/luatweaker_test/src/client/hud.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/network_test.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/storage_test.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/events_test.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/math_test.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/world_test.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/dynamic_bridge.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/skills.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/boss_ai.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/recipes.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/commands.lua",
            "neoforge-platform/luamods/luatweaker_test/src/server/mana_system.lua",
            "neoforge-platform/luamods/luatweaker_test/src/startup/content.lua",
            "neoforge-platform/luamods/luatweaker_test/src/startup/dimension.lua",
            "neoforge-platform/luamods/luatweaker_test/main.lua"
        };
        for (String rel : relativePaths) {
            java.io.File candidate = new java.io.File("../../" + rel);
            if (!candidate.exists()) {
                candidate = new java.io.File("../" + rel);
            }
            assertTrue(candidate.exists(), "Script must be found: " + candidate.getAbsolutePath());
            String source = java.nio.file.Files.readString(candidate.toPath());
            String error = LuaEngineFactory.checkSyntax(candidate.getName(), source);
            assertNull(error, candidate.getName() + " has syntax errors: " + error);
        }
    }

    @Test
    public void requireResolvesClientModules() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        ILuaTable gui = engine.createTable();
        gui.rawset("DrawRect", args -> engine.nilValue());
        globals.rawset("GuiService", gui);

        ILuaTable run = engine.createTable();
        globals.rawset("RunService", run);

        ILuaTable key = engine.createTable();
        globals.rawset("KeyBindService", key);

        engine.executeString(
            "local gui = require('LuaTweaker.GuiService')\n" +
            "assert(gui ~= nil, 'LuaTweaker.GuiService must resolve')\n" +
            "local run = require('LuaTweaker.RunService')\n" +
            "assert(run ~= nil, 'LuaTweaker.RunService must resolve')\n" +
            "local key = require('LuaTweaker.KeyBindService')\n" +
            "assert(key ~= nil, 'LuaTweaker.KeyBindService must resolve')",
            "repro_require"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }
}
