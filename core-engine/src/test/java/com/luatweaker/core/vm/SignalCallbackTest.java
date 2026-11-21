package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the Signal/task pipeline:
 * 1. Signal:Fire must pass only the real arguments to listeners (no arg shift from self).
 * 2. Signal:FireSync must invoke listeners immediately on the calling thread.
 * 3. A crashing deferred listener must not stop the remaining tasks, and no VM
 *    control-flow exception may leak to Java through task._tick.
 * 4. require("LuaTweaker.GuiService" / RunService / KeyBindService) must resolve.
 */
class SignalCallbackTest {

    private static final String SIGNAL = "Signal";
    private static final String REPRO_SIGNAL = "_reproSignal";
    private static final String SYNC_SIGNAL = "_syncSignal";
    private static final String TASK_GLOBAL = "task";
    private static final String TICK_FUNCTION = "_tick";
    private static final String REPRO_FIRST = "_reproFirst";
    private static final String REPRO_SECOND = "_reproSecond";

    private static final String CONNECT_SCRIPT = """
            _reproSignal:Connect(function(first, second)
                _reproFirst = first
                _reproSecond = second
            end)
            """;

    private static final String COLON_SCRIPT = """
            _reproSignal:Connect(function(value)
                print('[Repro] listener ran, value=' .. tostring(value))
                _reproValue = value
            end)
            print('[Repro] before fire')
            _reproSignal:Fire('hello')
            print('[Repro] after fire')
            require('LuaTweaker.Task')._tick()
            print('[Repro] after tick, _reproValue=' .. tostring(_reproValue))
            """;

    private static final String FIRESYNC_SCRIPT = """
            _syncSignal:Connect(function(value) _syncFirst = value end)
            _syncSignal:FireSync('now')
            assert(_syncFirst == 'now', 'FireSync must run listeners before returning')
            """;

    private static final String CRASHING_LISTENER_SCRIPT = """
            _reproSignal:Connect(function(player)
                player:getUuid()
            end)
            _reproSignal:Connect(function(player)
                _reproSurvived = true
            end)
            """;

    private static final String YIELD_SCRIPT = """
            Task.spawn(function()
                _yieldStep = 'started'
                Task.wait(0.01)
                _yieldStep = 'resumed'
            end)
            """;

    private static final String DEFER_DELAY_SCRIPT = """
            Task.defer(function() _deferRan = true end)
            Task.delay(0, function() _delayRan = true end)
            assert(_deferRan == nil, 'deferred task must not run before the tick')
            assert(_delayRan == nil, 'delayed task must not run before the tick')
            """;

    private static final String[] RELATIVE_PATHS = {
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

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaTable createSignal(ILuaEngine engine, ILuaValue signalClass) {
        ILuaValue newSignalFn = signalClass.asTable().rawget("new");
        ILuaValue signal = engine.callFunction(newSignalFn, signalClass);
        assertNotNull(signal, "Signal.new returned nil");
        return signal.asTable();
    }

    private static ILuaValue tickFunction(ILuaTable globals) {
        return globals.rawget(TASK_GLOBAL).asTable().rawget(TICK_FUNCTION);
    }

    @Nested
    class SignalFireFromJava {

        private ILuaEngine connectedSignal() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaTable signal = createSignal(engine, globals.rawget(SIGNAL));
            globals.rawset(REPRO_SIGNAL, signal);
            engine.executeString(CONNECT_SCRIPT, "repro_connect");
            return engine;
        }

        private ILuaEngine firedWithPlayerAndTicked() {
            ILuaEngine engine = connectedSignal();
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaValue fireFn = globals.rawget(SIGNAL).asTable().rawget("Fire");
            ILuaTable playerTable = engine.createTable();
            playerTable.rawset("Name", engine.wrapString("Dev"));
            engine.callFunction(fireFn, globals.rawget(REPRO_SIGNAL), playerTable, engine.wrapNumber(42));
            engine.callFunction(tickFunction(globals));
            return engine;
        }

        @Test
        void fireSignalFromJavaPassesArgsAfterSelfSignalClassMissingFromBootstrap() {
            ILuaEngine engine = connectedSignal();
            assertNotNull(engine.getGlobalEnvironment().rawget(SIGNAL),
                    "Signal class missing from bootstrap");
        }

        @Test
        void fireSignalFromJavaPassesArgsAfterSelfListenerDidNotReceiveFirstArgument() {
            ILuaEngine engine = connectedSignal();
            assertNotNull(engine.getGlobalEnvironment().rawget(REPRO_FIRST),
                    "listener did not receive first argument");
        }

        @Test
        void fireSignalFromJavaPassesArgsAfterSelfFirstArgumentMustBeThePlayerTableGot() {
            ILuaEngine engine = firedWithPlayerAndTicked();
            ILuaValue first = engine.getGlobalEnvironment().rawget(REPRO_FIRST);
            assertTrue(first.isTable(), "first argument must be the player table, got: " + first.asString());
        }

        @Test
        void fireSignalFromJavaPassesArgsAfterSelfSecondArgumentMustBePreserved() {
            ILuaEngine engine = firedWithPlayerAndTicked();
            ILuaValue second = engine.getGlobalEnvironment().rawget(REPRO_SECOND);
            assertEquals(42.0, second.asDouble(), "second argument must be preserved");
        }
    }

    @Nested
    class SignalFireFromLua {

        private ILuaEngine firedThroughColonSyntax() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaTable signal = createSignal(engine, globals.rawget(SIGNAL));
            globals.rawset(REPRO_SIGNAL, signal);
            engine.executeString(COLON_SCRIPT, "repro_colon");
            return engine;
        }

        @Test
        void fireSignalWithColonSyntaxFromLuaListenerDidNotRun() {
            ILuaEngine engine = firedThroughColonSyntax();
            assertNotNull(engine.getGlobalEnvironment().rawget("_reproValue"), "listener did not run");
        }

        @Test
        void fireSignalWithColonSyntaxFromLuaHello() {
            ILuaEngine engine = firedThroughColonSyntax();
            assertEquals("hello", engine.getGlobalEnvironment().rawget("_reproValue").asString(),
                    "Fire must deliver the Lua argument without any self shift");
        }
    }

    @Nested
    class FireSync {

        private ILuaEngine firedSync() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaTable signal = createSignal(engine, globals.rawget(SIGNAL));
            globals.rawset(SYNC_SIGNAL, signal);
            engine.executeString(FIRESYNC_SCRIPT, "repro_firesync");
            return engine;
        }

        @Test
        void fireSyncInvokesListenersImmediatelyOnCallingThreadGetAndClearLastExecutionError() {
            ILuaEngine engine = firedSync();
            assertNull(engine.getAndClearLastExecutionError(),
                    "FireSync must run its listener synchronously without a Lua error");
        }

        @Test
        void fireSyncInvokesListenersImmediatelyOnCallingThreadSyncFirst() {
            ILuaEngine engine = firedSync();
            assertEquals("now", engine.getGlobalEnvironment().rawget("_syncFirst").asString(),
                    "the FireSync listener must have observed the fired value");
        }

        @Test
        void fireSyncInvokesListenersImmediatelyOnCallingThreadSyncSurvivor() {
            ILuaEngine engine = firedSync();
            ILuaTable globals = engine.getGlobalEnvironment();
            // Error path: a crashing listener is contained by pcall inside FireSync and
            // must not prevent listeners registered after it from running.
            engine.executeString("""
                    _syncSignal:Connect(function() error('listener boom') end)
                    _syncSignal:Connect(function() _syncSurvivor = true end)
                    _syncSignal:FireSync('again')
                    """, "repro_firesync_error");
            assertTrue(globals.rawget("_syncSurvivor").asBoolean(),
                    "a healthy listener registered after a crashing one must still run under FireSync");
        }
    }

    @Nested
    class DeferredCrashIsolation {

        private ILuaEngine crashedSignalFired() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaTable signal = createSignal(engine, globals.rawget(SIGNAL));
            globals.rawset(REPRO_SIGNAL, signal);
            engine.executeString(CRASHING_LISTENER_SCRIPT, "repro_crash");
            ILuaValue fireFn = globals.rawget(SIGNAL).asTable().rawget("Fire");
            engine.callFunction(fireFn, signal, engine.createTable());
            return engine;
        }

        @Test
        void crashingDeferredListenerDoesNotBlockRemainingTasksCallFunction() {
            ILuaEngine engine = crashedSignalFired();
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaValue tickFn = tickFunction(globals);
            assertDoesNotThrow(() -> engine.callFunction(tickFn),
                    "task._tick must not leak a VM control-flow exception to Java when a listener crashes");
        }

        @Test
        void crashingDeferredListenerDoesNotBlockRemainingTasksSurvived() {
            ILuaEngine engine = crashedSignalFired();
            ILuaTable globals = engine.getGlobalEnvironment();
            engine.callFunction(tickFunction(globals));
            ILuaValue survived = globals.rawget("_reproSurvived");
            assertTrue(survived != null && survived.asBoolean(),
                    "the healthy listener queued after the crashing one must still run");
        }
    }

    @Nested
    class DeferredTasks {

        private ILuaEngine spawnedYieldingTask() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            engine.executeString(YIELD_SCRIPT, "repro_yield");
            engine.callFunction(tickFunction(engine.getGlobalEnvironment()));
            return engine;
        }

        @Test
        void deferredTaskWithWaitYieldsAndResumesAcrossTicksStarted() {
            ILuaEngine engine = spawnedYieldingTask();
            assertEquals("started", engine.getGlobalEnvironment().rawget("_yieldStep").asString(),
                    "deferred task must start and yield");
        }

        @Test
        void deferredTaskWithWaitYieldsAndResumesAcrossTicksResumed() {
            ILuaEngine engine = spawnedYieldingTask();
            ILuaTable globals = engine.getGlobalEnvironment();
            AwaitSupport.awaitMillis(50);
            engine.callFunction(tickFunction(globals));
            assertEquals("resumed", globals.rawget("_yieldStep").asString(),
                    "task.wait must resume the coroutine on a later tick");
        }
    }

    @Nested
    class DeferAndDelay {

        private ILuaEngine deferredThenTicked() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            engine.executeString(DEFER_DELAY_SCRIPT, "repro_defer_delay");
            engine.callFunction(tickFunction(engine.getGlobalEnvironment()));
            return engine;
        }

        @Test
        void taskDeferAndDelayQueueWorkUntilTickGetAndClearLastExecutionError() {
            ILuaEngine engine = deferredThenTicked();
            assertNull(engine.getAndClearLastExecutionError(),
                    "defer/delay bookkeeping and the tick must not raise a Lua error");
        }

        @Test
        void taskDeferAndDelayQueueWorkUntilTickDeferRan() {
            ILuaEngine engine = deferredThenTicked();
            assertTrue(engine.getGlobalEnvironment().rawget("_deferRan").asBoolean(),
                    "Task.defer work must run on tick");
        }

        @Test
        void taskDeferAndDelayQueueWorkUntilTickDelayRan() {
            ILuaEngine engine = deferredThenTicked();
            assertTrue(engine.getGlobalEnvironment().rawget("_delayRan").asBoolean(),
                    "Task.delay(0) work must run on tick");
        }
    }

    @Nested
    class LuaScriptCatalog {

        private void validateLuaScriptSyntax(String rel) throws IOException {
            File candidate = new File("../../" + rel);
            if (!candidate.exists()) {
                candidate = new File("../" + rel);
            }
            assertTrue(candidate.exists(), "Script must be found: " + candidate.getAbsolutePath());
            String source = Files.readString(candidate.toPath());
            String error = LuaEngineFactory.checkSyntax(candidate.getName(), source);
            assertNull(error, candidate.getName() + " has syntax errors: " + error);
        }

        @Test
        void modifiedLuaScriptsAreSyntacticallyValid() {
            for (String rel : RELATIVE_PATHS) {
                assertDoesNotThrow(() -> validateLuaScriptSyntax(rel),
                        "ship script must parse: " + rel);
            }
        }
    }

    @Nested
    class ClientModules {

        @Test
        void requireResolvesClientModules() {
            ILuaEngine engine = LuaEngineFactory.create(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            globals.rawset("GuiService", engine.createTable());
            globals.rawset("RunService", engine.createTable());
            globals.rawset("KeyBindService", engine.createTable());

            engine.executeString("""
                    local gui = require('LuaTweaker.GuiService')
                    assert(gui ~= nil, 'LuaTweaker.GuiService must resolve')
                    local run = require('LuaTweaker.RunService')
                    assert(run ~= nil, 'LuaTweaker.RunService must resolve')
                    local key = require('LuaTweaker.KeyBindService')
                    assert(key ~= nil, 'LuaTweaker.KeyBindService must resolve')
                    """, "repro_require");
            assertNull(engine.getAndClearLastExecutionError(),
                    "the three client service modules must resolve through require");
        }
    }
}
