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
    void taskCancelStopsQueuedWork() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeStringOrThrow(
            "_deferRan = nil\n" +
            "_delayRan = nil\n" +
            "_deferHandle = task.defer(function() _deferRan = true end)\n" +
            "_delayHandle = task.delay(0.01, function() _delayRan = true end)\n" +
            "assert(task.cancel(_deferHandle) == true, 'a queued task must be cancellable')\n" +
            "assert(task.cancel(_delayHandle) == true, 'a delayed task must be cancellable')\n" +
            "assert(task.cancel(_deferHandle) == false, 'cancelling twice must report false')",
            "task_cancel");

        engine.callFunction(tickFunction(engine));
        sleepForPollInterval();
        engine.callFunction(tickFunction(engine));

        assertTrue(globals.rawget("_deferRan") == null || globals.rawget("_deferRan").isNil(),
                "a cancelled deferred task must not run");
        assertTrue(globals.rawget("_delayRan") == null || globals.rawget("_delayRan").isNil(),
                "a cancelled delayed task must not run");
    }

    @Test
    void taskCancelAfterCompletionReportsFalse() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        engine.executeStringOrThrow(
            "_ran = 0\n" +
            "_handle = task.spawn(function() _ran = _ran + 1 end)",
            "task_cancel_after");
        engine.callFunction(tickFunction(engine));
        assertEquals(1.0, globals.rawget("_ran").asDouble(), "spawn must return a runnable handle");

        engine.executeStringOrThrow(
            "assert(task.cancel(_handle) == false, 'a task that already ran cannot be cancelled')",
            "task_cancel_after_check");
    }

    @Test
    void taskCancelIgnoresUnknownValues() {
        ILuaEngine engine = new LuavaEngine(false);
        engine.executeStringOrThrow(
            "assert(task.cancel(nil) == false)\n" +
            "assert(task.cancel('not a handle') == false)\n" +
            "assert(task.cancel({}) == false)",
            "task_cancel_unknown");
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

    private static ILuaTable readyState(ILuaEngine engine, String status, ILuaValue value) {
        ILuaTable state = engine.createTable();
        state.rawset("Status", engine.wrapString(status));
        if (value != null) {
            state.rawset("Value", value);
        }
        return state;
    }

    @Test
    void remoteFunctionRequestReceivesResponse() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaTable network = engine.createTable();
        network.rawset("_RpcInvokeServer", args ->
                // The answer is ready on the very first poll, so this call does not need to yield.
                engine.wrapString("req-immediate"));
        network.rawset("_RpcPoll", args -> {
            ILuaTable response = engine.createTable();
            response.rawset("ok", true);
            response.rawset("echo", engine.createTable());
            return readyState(engine, "done", response);
        });
        network.rawset("_RpcCancel", args -> engine.nilValue());
        globals.rawset("NetworkService", network);

        engine.executeString(
            "local remote = RemoteFunction:new('EchoFunc')\n" +
            "local response = remote:InvokeServer(7)\n" +
            "assert(response ~= nil, 'a response must be delivered')\n" +
            "assert(response.ok == true)",
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
        network.rawset("_RpcInvokeServer", args -> {
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
    void remoteFunctionPendingOutsideACoroutineFailsLoudly() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable network = engine.createTable();
        network.rawset("_RpcInvokeServer", args -> engine.wrapString("req-pending"));
        network.rawset("_RpcPoll", args -> readyState(engine, "pending", null));
        final boolean[] cancelled = {false};
        network.rawset("_RpcCancel", args -> {
            cancelled[0] = true;
            return engine.nilValue();
        });
        engine.getGlobalEnvironment().rawset("NetworkService", network);

        engine.executeString(
            "local remote = RemoteFunction:new('SlowFunc')\n" +
            "local ok, err = pcall(function() return remote:InvokeServer(1) end)\n" +
            "assert(not ok, 'a call that must wait cannot run outside a coroutine')\n" +
            "_rpcError = tostring(err)",
            "remote_function_no_coroutine"
        );
        assertNull(engine.getAndClearLastExecutionError());
        String message = engine.getGlobalEnvironment().rawget("_rpcError").asString();
        assertTrue(message.contains("coroutine"),
                "the error must explain the coroutine requirement: " + message);
        assertTrue(cancelled[0], "the orphaned request must be cancelled");
    }

    @Test
    void remoteFunctionYieldsUntilTheResponseArrives() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        final boolean[] answered = {false};
        ILuaTable network = engine.createTable();
        network.rawset("_RpcInvokeServer", args -> engine.wrapString("req-late"));
        network.rawset("_RpcPoll", args -> answered[0]
                ? readyState(engine, "done", engine.wrapNumber(99))
                : readyState(engine, "pending", null));
        network.rawset("_RpcCancel", args -> engine.nilValue());
        globals.rawset("NetworkService", network);

        engine.executeString(
            "Task.spawn(function()\n" +
            "    _rpcPhase = 'waiting'\n" +
            "    _rpcValue = RemoteFunction:new('LateFunc'):InvokeServer(5)\n" +
            "    _rpcPhase = 'done'\n" +
            "end)",
            "remote_function_wait"
        );
        assertNull(engine.getAndClearLastExecutionError());

        engine.callFunction(tickFunction(engine));
        assertEquals("waiting", globals.rawget("_rpcPhase").asString(),
                "the caller must be suspended while the request is in flight");

        answered[0] = true;
        sleepForPollInterval();
        engine.callFunction(tickFunction(engine));
        assertEquals("done", globals.rawget("_rpcPhase").asString(),
                "the caller must resume once the answer arrives");
        assertEquals(99.0, globals.rawget("_rpcValue").asDouble());
    }

    @Test
    void remoteFunctionTimesOutWhenNoAnswerArrives() {
        ILuaEngine engine = new LuavaEngine(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaTable network = engine.createTable();
        network.rawset("_RpcInvokeServer", args -> engine.wrapString("req-never"));
        network.rawset("_RpcPoll", args -> readyState(engine, "pending", null));
        network.rawset("_RpcCancel", args -> engine.nilValue());
        globals.rawset("NetworkService", network);

        // Fast-forward the injected clock past the 10s RPC timeout.
        engine.executeString(
            "local fakeClock = 0\n" +
            "task.getTimeClock = function() fakeClock = fakeClock + 11 return fakeClock end\n" +
            "Task.spawn(function()\n" +
            "    local ok, err = pcall(function() return RemoteFunction:new('NeverFunc'):InvokeServer(1) end)\n" +
            "    assert(not ok, 'an unanswered request must fail')\n" +
            "    _rpcTimeout = tostring(err)\n" +
            "end)",
            "remote_function_timeout"
        );
        assertNull(engine.getAndClearLastExecutionError());

        engine.callFunction(tickFunction(engine));
        String message = globals.rawget("_rpcTimeout").asString();
        assertTrue(message.contains("timed out"),
                "the error must report the timeout: " + message);
    }

    private static void sleepForPollInterval() {
        try {
            // RPC poll interval is 50ms; wait long enough for the scheduled wait to be due.
            Thread.sleep(80);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the RPC poll interval: " + e.getMessage());
        }
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
