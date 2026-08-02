package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the Signal/task pipeline fixes:
 * 1. Signal:Fire must pass only the real arguments to listeners (no arg shift from self).
 * 2. A crashing deferred listener must never leak Cobalt's UnwindThrowable to Java,
 *    and remaining deferred tasks must still run.
 * 3. require("LuaTweaker.GuiService" / RunService / KeyBindService) must resolve.
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
        ILuaEngine engine = new CobaltLuaEngine();
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
        ILuaEngine engine = new CobaltLuaEngine();
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
    public void crashingListenerDoesNotLeakUnwindThrowableAndOtherTasksStillRun() {
        ILuaEngine engine = new CobaltLuaEngine();
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
            "task._tick must not leak UnwindThrowable to Java when a listener crashes");

        ILuaValue survived = globals.rawget("_reproSurvived");
        assertTrue(survived != null && survived.asBoolean(),
            "the healthy listener queued after the crashing one must still run");
    }

    @Test
    public void deferredTaskWithWaitYieldsAndResumesAcrossTicks() throws Exception {
        ILuaEngine engine = new CobaltLuaEngine();
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
    public void modifiedLuaScriptsAreSyntacticallyValid() throws Exception {
        String[] relativePaths = {
            "neoforge-platform/luamods/ruby_mod/src/client/magic_staff_client.lua",
            "neoforge-platform/luamods/ruby_mod/src/server/magic_staff.lua",
            "neoforge-platform/luamods/ruby_mod/src/server/ruby_boss.lua",
            "neoforge-platform/luamods/ruby_mod/src/startup/ruby_content.lua",
            "neoforge-platform/luamods/ruby_mod/main.lua",
            "neoforge-platform/luamods/my_custom_mod/main.lua",
            "neoforge-platform/luamods/my_custom_mod/src/server/boss_ai.lua"
        };
        for (String rel : relativePaths) {
            java.io.File candidate = new java.io.File("../../" + rel);
            if (!candidate.exists()) {
                candidate = new java.io.File("../" + rel);
            }
            assertTrue(candidate.exists(), "Script must be found: " + candidate.getAbsolutePath());
            String source = java.nio.file.Files.readString(candidate.toPath());
            String error = com.luatweaker.core.engine.LuaEngine.checkSyntax(candidate.getName(), source);
            assertNull(error, candidate.getName() + " has syntax errors: " + error);
        }
    }

    @Test
    public void requireResolvesClientModules() {        ILuaEngine engine = new CobaltLuaEngine();
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
    }
}
