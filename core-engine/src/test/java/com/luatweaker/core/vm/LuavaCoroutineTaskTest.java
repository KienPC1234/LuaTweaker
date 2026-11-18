package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the coroutine-backed Task/Signal bridge and RemoteFunction responses on luava. */
public class LuavaCoroutineTaskTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaValue tickFunction(ILuaEngine engine) {
        ILuaValue tickFn = engine.getGlobalEnvironment().rawget("task").asTable().rawget("_tick");
        assertNotNull(tickFn, "task._tick must exist");
        return tickFn;
    }

    @Test
    void spawnRunsDeferredWorkOnTheNextTick() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeString(
            "Task.spawn(function(value) _spawnedValue = value end, 'spawned')\n" +
            "assert(_spawnedValue == nil, 'spawn must not run synchronously')",
            "spawn_check"
        );
        assertNull(engine.getAndClearLastExecutionError());

        engine.callFunction(tickFunction(engine));
        assertEquals("spawned", globals.rawget("_spawnedValue").asString());
    }

    @Test
    void deferAndDelayQueueWorkUntilTick() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeString(
            "Task.defer(function() _deferRan = true end)\n" +
            "Task.delay(0, function() _delayRan = true end)\n" +
            "assert(_deferRan == nil and _delayRan == nil)",
            "defer_delay_check"
        );
        assertNull(engine.getAndClearLastExecutionError());

        engine.callFunction(tickFunction(engine));
        assertTrue(globals.rawget("_deferRan").asBoolean(), "deferred work must run on tick");
        assertTrue(globals.rawget("_delayRan").asBoolean(), "zero-delay work must run on tick");
    }

    @Test
    void taskWaitSuspendsAndResumesTheCoroutine() throws Exception {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeString(
            "Task.spawn(function()\n" +
            "    _waitPhase = 'before'\n" +
            "    Task.wait(0.01)\n" +
            "    _waitPhase = 'after'\n" +
            "end)",
            "wait_check"
        );
        engine.callFunction(tickFunction(engine));
        assertEquals("before", globals.rawget("_waitPhase").asString(), "coroutine must be suspended");

        Thread.sleep(50);
        engine.callFunction(tickFunction(engine));
        assertEquals("after", globals.rawget("_waitPhase").asString(), "coroutine must resume after the wait");
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void signalFireAndFireSyncDeliverArguments() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeString(
            "_asyncSignal = Signal.new()\n" +
            "_asyncSignal:Connect(function(value) _asyncValue = value end)\n" +
            "_asyncSignal:Fire('async')\n" +
            "assert(_asyncValue == nil, 'Fire must defer listeners')\n" +
            "_syncSignal = Signal.new()\n" +
            "_syncSignal:Connect(function(value) _syncValue = value end)\n" +
            "_syncSignal:FireSync('sync')\n" +
            "assert(_syncValue == 'sync', 'FireSync must run listeners immediately')",
            "signal_check"
        );
        assertNull(engine.getAndClearLastExecutionError());

        engine.callFunction(tickFunction(engine));
        assertEquals("async", globals.rawget("_asyncValue").asString(), "Fire listener must run on tick");
        assertEquals("sync", globals.rawget("_syncValue").asString());
    }

    @Test
    void remoteFunctionRequestReceivesResponse() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaTable network = engine.createTable();
        network.rawset("InvokeServer", args -> {
            ILuaTable response = engine.createTable();
            response.rawset("ok", true);
            response.rawset("echo", args[2]);
            return response;
        });
        globals.rawset("NetworkService", network);

        engine.executeString(
            "local remote = RemoteFunction:new('EchoFunc')\n" +
            "local response = remote:InvokeServer(7)\n" +
            "assert(response ~= nil, 'a response must be delivered')\n" +
            "assert(response.ok == true)\n" +
            "assert(response.echo[1] == 7, 'request payload must round-trip')",
            "remote_function_success"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void remoteFunctionWithoutTransportReturnsNil() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.executeString(
            "local remote = RemoteFunction:new('NoTransport')\n" +
            "local response = remote:InvokeServer(1)\n" +
            "assert(response == nil, 'no transport means no response')",
            "remote_function_no_transport"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void remoteFunctionTransportFailureIsContained() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable network = engine.createTable();
        network.rawset("InvokeServer", args -> {
            throw new IllegalStateException("simulated transport failure");
        });
        engine.getGlobalEnvironment().rawset("NetworkService", network);

        engine.executeString(
            "local remote = RemoteFunction:new('FailFunc')\n" +
            "local ok, err = pcall(function() return remote:InvokeServer(1) end)\n" +
            "assert(not ok, 'a transport failure must surface as a Lua error')\n" +
            "assert(err ~= nil and tostring(err) ~= '', 'the error must carry a message')\n" +
            "assert(_G ~= nil, 'the VM must remain usable after the failure')",
            "remote_function_error"
        );
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void taskWaitDoesNotStallTickAndCanReenterJava() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        // A Java binding that calls back into the engine, like every *Service binding does.
        ILuaTable service = engine.createTable();
        service.rawset("touch", args -> {
            engine.wrapNumber(1.0);
            return engine.wrapString("ok");
        });
        globals.rawset("Svc", service);

        engine.executeString(
            "Task.spawn(function()\n" +
            "    _phase = 'start'\n" +
            "    _java = Svc:touch()\n" +
            "    Task.wait(0.01)\n" +
            "    _phase = 'done'\n" +
            "end)",
            "wait_reentry"
        );
        assertNull(engine.getAndClearLastExecutionError());

        long startNanos = System.nanoTime();
        engine.callFunction(tickFunction(engine));
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertEquals("start", globals.rawget("_phase").asString(),
            "the task must suspend at Task.wait");
        assertEquals("ok", globals.rawget("_java").asString(),
            "the task must be able to call Java bindings before waiting");
        // Before the execution-gate fix this tick blocked ~2s on the coroutine/engine monitor.
        assertTrue(elapsedMillis < 500L,
            "Task.wait must not stall the tick, took " + elapsedMillis + " ms");

        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the task delay");
        }
        engine.callFunction(tickFunction(engine));
        assertEquals("done", globals.rawget("_phase").asString(),
            "the task must resume after its delay elapses");
        assertNull(engine.getAndClearLastExecutionError());
    }

    @Test
    void onceFiresOnlyOnceWhenSignalFiresTwiceBeforeTick() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeString(
            "_onceCount = 0\n" +
            "local s = Signal.new()\n" +
            "s:Once(function() _onceCount = _onceCount + 1 end)\n" +
            "s:Fire()\n" +
            "s:Fire()",
            "once_twice"
        );
        assertNull(engine.getAndClearLastExecutionError());

        engine.callFunction(tickFunction(engine));
        assertEquals(1.0, globals.rawget("_onceCount").asDouble(),
            "a Once listener must not run twice when Fire is called twice in one tick");
    }

    @Test
    void fireSyncRunsAllListenersWhenOneDisconnectsItself() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeString(
            "_syncRan = 0\n" +
            "local s = Signal.new()\n" +
            "s:Once(function() _syncRan = _syncRan + 1 end)\n" +
            "s:Connect(function() _syncRan = _syncRan + 10 end)\n" +
            "s:FireSync()",
            "firesync_disconnect"
        );
        assertNull(engine.getAndClearLastExecutionError());

        assertEquals(11.0, globals.rawget("_syncRan").asDouble(),
            "a listener disconnecting itself must not skip the next listener");
    }

    @Test
    void shutdownStopsSuspendedTasksAndRejectsFurtherCalls() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeString(
            "Task.spawn(function() Task.wait(10) _afterShutdown = true end)",
            "shutdown_task"
        );
        engine.callFunction(tickFunction(engine));
        assertTrue(globals.rawget("_afterShutdown").isNil());

        engine.shutdown();
        assertThrows(IllegalStateException.class, () -> engine.callFunction(tickFunction(engine)),
            "a shut-down engine must reject further execution");
    }
}
