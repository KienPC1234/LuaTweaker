package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the coroutine-backed Task/Signal bridge and RemoteFunction responses on luava. */
class LuavaCoroutineTaskTest {

    private static final String RPC_INVOKE = "_RpcInvokeServer";
    private static final String RPC_POLL = "_RpcPoll";
    private static final String RPC_CANCEL = "_RpcCancel";
    private static final String NETWORK_SERVICE = "NetworkService";
    private static final String STATUS = "Status";
    private static final String VALUE = "Value";
    private static final String DONE = "done";
    private static final String PENDING = "pending";

    private static final String SPAWN_SCRIPT = """
            Task.spawn(function(value) _spawnedValue = value end, 'spawned')
            assert(_spawnedValue == nil, 'spawn must not run synchronously')
            """;

    private static final String DEFER_DELAY_SCRIPT = """
            Task.defer(function() _deferRan = true end)
            Task.delay(0, function() _delayRan = true end)
            assert(_deferRan == nil and _delayRan == nil)
            """;

    private static final String CANCEL_SCRIPT = """
            _deferRan = nil
            _delayRan = nil
            _deferHandle = task.defer(function() _deferRan = true end)
            _delayHandle = task.delay(0.01, function() _delayRan = true end)
            assert(task.cancel(_deferHandle) == true, 'a queued task must be cancellable')
            assert(task.cancel(_delayHandle) == true, 'a delayed task must be cancellable')
            assert(task.cancel(_deferHandle) == false, 'cancelling twice must report false')
            """;

    private static final String WAIT_SCRIPT = """
            Task.spawn(function()
                _waitPhase = 'before'
                Task.wait(0.01)
                _waitPhase = 'after'
            end)
            """;

    private static final String SIGNAL_SCRIPT = """
            _asyncSignal = Signal.new()
            _asyncSignal:Connect(function(value) _asyncValue = value end)
            _asyncSignal:Fire('async')
            assert(_asyncValue == nil, 'Fire must defer listeners')
            _syncSignal = Signal.new()
            _syncSignal:Connect(function(value) _syncValue = value end)
            _syncSignal:FireSync('sync')
            assert(_syncValue == 'sync', 'FireSync must run listeners immediately')
            """;

    private static final String SLOW_CALL_SCRIPT = """
            local remote = RemoteFunction:new('SlowFunc')
            local ok, err = pcall(function() return remote:InvokeServer(1) end)
            assert(not ok, 'a call that must wait cannot run outside a coroutine')
            _rpcError = tostring(err)
            """;

    private static final String LATE_CALL_SCRIPT = """
            Task.spawn(function()
                _rpcPhase = 'waiting'
                _rpcValue = RemoteFunction:new('LateFunc'):InvokeServer(5)
                _rpcPhase = 'done'
            end)
            """;

    private static final String TIMEOUT_CALL_SCRIPT = """
            local fakeClock = 0
            task.getTimeClock = function() fakeClock = fakeClock + 11 return fakeClock end
            Task.spawn(function()
                local ok, err = pcall(function() return RemoteFunction:new('NeverFunc'):InvokeServer(1) end)
                assert(not ok, 'an unanswered request must fail')
                _rpcTimeout = tostring(err)
            end)
            """;

    private static final String WAIT_REENTRY_SCRIPT = """
            Task.spawn(function()
                _phase = 'start'
                _java = Svc:touch()
                Task.wait(0.01)
                _phase = 'done'
            end)
            """;

    private static final String ONCE_SCRIPT = """
            _onceCount = 0
            local s = Signal.new()
            s:Once(function() _onceCount = _onceCount + 1 end)
            s:Fire()
            s:Fire()
            """;

    private static final String FIRESYNC_DISCONNECT_SCRIPT = """
            _syncRan = 0
            local s = Signal.new()
            s:Once(function() _syncRan = _syncRan + 1 end)
            s:Connect(function() _syncRan = _syncRan + 10 end)
            s:FireSync()
            """;

    private static final String SHUTDOWN_SCRIPT =
            "Task.spawn(function() Task.wait(10) _afterShutdown = true end)";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaValue tickFunction(ILuaEngine engine) {
        ILuaValue tickFn = engine.getGlobalEnvironment().rawget("task").asTable().rawget("_tick");
        assertNotNull(tickFn, "task._tick must exist");
        return tickFn;
    }

    private static ILuaTable readyState(ILuaEngine engine, String status, ILuaValue value) {
        ILuaTable state = engine.createTable();
        state.rawset(STATUS, engine.wrapString(status));
        if (value != null) {
            state.rawset(VALUE, value);
        }
        return state;
    }

    private static void sleepForPollInterval() {
        // RPC poll interval is 50ms; wait long enough for the scheduled wait to be due.
        AwaitSupport.awaitMillis(80);
    }

    @Nested
    class SpawnAndDefer {

        private ILuaEngine tickedAfterSpawn() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(SPAWN_SCRIPT, "spawn_check");
            engine.callFunction(tickFunction(engine));
            return engine;
        }

        private ILuaEngine tickedAfterDeferAndDelay() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(DEFER_DELAY_SCRIPT, "defer_delay_check");
            engine.callFunction(tickFunction(engine));
            return engine;
        }

        @Test
        void spawnRunsDeferredWorkOnTheNextTickGetAndClearLastExecutionError() {
            ILuaEngine engine = tickedAfterSpawn();
            assertNull(engine.getAndClearLastExecutionError(),
                    "spawning and ticking must not raise a Lua error");
        }

        @Test
        void spawnRunsDeferredWorkOnTheNextTickSpawnedValue() {
            ILuaEngine engine = tickedAfterSpawn();
            assertEquals("spawned", engine.getGlobalEnvironment().rawget("_spawnedValue").asString(),
                    "the spawned closure must receive the value passed to Task.spawn");
        }

        @Test
        void deferAndDelayQueueWorkUntilTickGetAndClearLastExecutionError() {
            ILuaEngine engine = tickedAfterDeferAndDelay();
            assertNull(engine.getAndClearLastExecutionError(),
                    "defer/delay bookkeeping and the tick must not raise a Lua error");
        }

        @Test
        void deferAndDelayQueueWorkUntilTickDeferRan() {
            ILuaEngine engine = tickedAfterDeferAndDelay();
            assertTrue(engine.getGlobalEnvironment().rawget("_deferRan").asBoolean(),
                    "deferred work must run on tick");
        }

        @Test
        void deferAndDelayQueueWorkUntilTickDelayRan() {
            ILuaEngine engine = tickedAfterDeferAndDelay();
            assertTrue(engine.getGlobalEnvironment().rawget("_delayRan").asBoolean(),
                    "zero-delay work must run on tick");
        }
    }

    @Nested
    class TaskCancel {

        private ILuaEngine cancelledTasksAfterTwoTicks() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeStringOrThrow(CANCEL_SCRIPT, "task_cancel");
            engine.callFunction(tickFunction(engine));
            sleepForPollInterval();
            engine.callFunction(tickFunction(engine));
            return engine;
        }

        @Test
        void taskCancelStopsQueuedWorkDeferRan() {
            ILuaEngine engine = cancelledTasksAfterTwoTicks();
            ILuaValue ran = engine.getGlobalEnvironment().rawget("_deferRan");
            assertTrue(ran == null || ran.isNil(), "a cancelled deferred task must not run");
        }

        @Test
        void taskCancelStopsQueuedWorkDelayRan() {
            ILuaEngine engine = cancelledTasksAfterTwoTicks();
            ILuaValue ran = engine.getGlobalEnvironment().rawget("_delayRan");
            assertTrue(ran == null || ran.isNil(), "a cancelled delayed task must not run");
        }

        @Test
        void taskCancelAfterCompletionReportsFalse() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable globals = engine.getGlobalEnvironment();

            engine.executeStringOrThrow("""
                    _ran = 0
                    _handle = task.spawn(function() _ran = _ran + 1 end)
                    """, "task_cancel_after");
            engine.callFunction(tickFunction(engine));
            assertEquals(1.0, globals.rawget("_ran").asDouble(), "spawn must return a runnable handle");

            engine.executeStringOrThrow(
                    "assert(task.cancel(_handle) == false, 'a task that already ran cannot be cancelled')",
                    "task_cancel_after_check");
        }

        @Test
        void taskCancelIgnoresUnknownValues() {
            ILuaEngine engine = new LuavaEngine(false);
            assertDoesNotThrow(
                    () -> engine.executeStringOrThrow("""
                            assert(task.cancel(nil) == false)
                            assert(task.cancel('not a handle') == false)
                            assert(task.cancel({}) == false)
                            """, "task_cancel_unknown"),
                    "cancelling unknown values must be safe no-ops");
        }
    }

    @Nested
    class TaskWait {

        private ILuaEngine waitedOnce() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(WAIT_SCRIPT, "wait_check");
            engine.callFunction(tickFunction(engine));
            return engine;
        }

        @Test
        void taskWaitSuspendsAndResumesTheCoroutineWaitPhase() {
            ILuaEngine engine = waitedOnce();
            assertEquals("before", engine.getGlobalEnvironment().rawget("_waitPhase").asString(),
                    "coroutine must be suspended");
        }

        @Test
        void taskWaitSuspendsAndResumesTheCoroutineWaitPhase2() {
            ILuaEngine engine = waitedOnce();
            AwaitSupport.awaitMillis(50);
            engine.callFunction(tickFunction(engine));
            assertEquals("after", engine.getGlobalEnvironment().rawget("_waitPhase").asString(),
                    "coroutine must resume after the wait");
        }

        @Test
        void taskWaitSuspendsAndResumesTheCoroutineGetAndClearLastExecutionError() {
            ILuaEngine engine = waitedOnce();
            AwaitSupport.awaitMillis(50);
            engine.callFunction(tickFunction(engine));
            assertNull(engine.getAndClearLastExecutionError(),
                    "suspending and resuming at Task.wait must not raise a Lua error");
        }
    }

    @Nested
    class Signals {

        private ILuaEngine tickedSignal() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(SIGNAL_SCRIPT, "signal_check");
            engine.callFunction(tickFunction(engine));
            return engine;
        }

        @Test
        void signalFireAndFireSyncDeliverArgumentsGetAndClearLastExecutionError() {
            ILuaEngine engine = tickedSignal();
            assertNull(engine.getAndClearLastExecutionError(),
                    "Fire and FireSync must both dispatch without a Lua error");
        }

        @Test
        void signalFireAndFireSyncDeliverArgumentsAsyncValue() {
            ILuaEngine engine = tickedSignal();
            assertEquals("async", engine.getGlobalEnvironment().rawget("_asyncValue").asString(),
                    "Fire listener must run on tick");
        }

        @Test
        void signalFireAndFireSyncDeliverArgumentsSyncValue() {
            ILuaEngine engine = tickedSignal();
            assertEquals("sync", engine.getGlobalEnvironment().rawget("_syncValue").asString(),
                    "FireSync listener must have run immediately with the fired value");
        }
    }

    @Nested
    class RemoteFunctionBasics {

        @Test
        void remoteFunctionRequestReceivesResponse() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaTable network = engine.createTable();
            network.rawset(RPC_INVOKE, args ->
                    // The answer is ready on the very first poll, so this call does not need to yield.
                    engine.wrapString("req-immediate"));
            network.rawset(RPC_POLL, args -> {
                ILuaTable response = engine.createTable();
                response.rawset("ok", true);
                response.rawset("echo", engine.createTable());
                return readyState(engine, DONE, response);
            });
            network.rawset(RPC_CANCEL, args -> engine.nilValue());
            globals.rawset(NETWORK_SERVICE, network);

            engine.executeString("""
                    local remote = RemoteFunction:new('EchoFunc')
                    local response = remote:InvokeServer(7)
                    assert(response ~= nil, 'a response must be delivered')
                    assert(response.ok == true)
                    """, "remote_function_success");
            assertNull(engine.getAndClearLastExecutionError(),
                    "an immediately-ready RemoteFunction response must be returned without error");
        }

        @Test
        void remoteFunctionWithoutTransportReturnsNil() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString("""
                    local remote = RemoteFunction:new('NoTransport')
                    local response = remote:InvokeServer(1)
                    assert(response == nil, 'no transport means no response')
                    """, "remote_function_no_transport");
            assertNull(engine.getAndClearLastExecutionError(),
                    "a missing NetworkService must yield a nil response, not an error");
        }

        @Test
        void remoteFunctionTransportFailureIsContained() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable network = engine.createTable();
            network.rawset(RPC_INVOKE, args -> {
                throw new IllegalStateException("simulated transport failure");
            });
            engine.getGlobalEnvironment().rawset(NETWORK_SERVICE, network);

            engine.executeString("""
                    local remote = RemoteFunction:new('FailFunc')
                    local ok, err = pcall(function() return remote:InvokeServer(1) end)
                    assert(not ok, 'a transport failure must surface as a Lua error')
                    assert(err ~= nil and tostring(err) ~= '', 'the error must carry a message')
                    assert(_G ~= nil, 'the VM must remain usable after the failure')
                    """, "remote_function_error");
            assertNull(engine.getAndClearLastExecutionError(),
                    "a contained transport failure must not poison the engine state");
        }
    }

    @Nested
    class RemoteFunctionPending {

        private ILuaEngine pendingNetworkEngine() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable network = engine.createTable();
            network.rawset(RPC_INVOKE, args -> engine.wrapString("req-pending"));
            network.rawset(RPC_POLL, args -> readyState(engine, PENDING, null));
            network.rawset(RPC_CANCEL, args -> engine.nilValue());
            engine.getGlobalEnvironment().rawset(NETWORK_SERVICE, network);
            return engine;
        }

        @Test
        void remoteFunctionPendingOutsideACoroutineFailsLoudlyGetAndClearLastExecutionError() {
            ILuaEngine engine = pendingNetworkEngine();
            engine.executeString(SLOW_CALL_SCRIPT, "remote_function_no_coroutine");
            assertNull(engine.getAndClearLastExecutionError(),
                    "a pcall-captured coroutine error must not leak into the engine state");
        }

        @Test
        void remoteFunctionPendingOutsideACoroutineFailsLoudlyCoroutine() {
            ILuaEngine engine = pendingNetworkEngine();
            engine.executeString(SLOW_CALL_SCRIPT, "remote_function_no_coroutine");
            String message = engine.getGlobalEnvironment().rawget("_rpcError").asString();
            assertTrue(message.contains("coroutine"),
                    "the error must explain the coroutine requirement: " + message);
        }

        @Test
        void remoteFunctionPendingOutsideACoroutineFailsLoudlyTheOrphanedRequestMustBeCancelled() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable network = engine.createTable();
            network.rawset(RPC_INVOKE, args -> engine.wrapString("req-pending"));
            network.rawset(RPC_POLL, args -> readyState(engine, PENDING, null));
            final boolean[] cancelled = {false};
            network.rawset(RPC_CANCEL, args -> {
                cancelled[0] = true;
                return engine.nilValue();
            });
            engine.getGlobalEnvironment().rawset(NETWORK_SERVICE, network);
            engine.executeString(SLOW_CALL_SCRIPT, "remote_function_no_coroutine");
            assertTrue(cancelled[0], "the orphaned request must be cancelled");
        }
    }

    @Nested
    class RemoteFunctionYield {

        private ILuaEngine lateNetworkEngine(AtomicBoolean answered) {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable network = engine.createTable();
            network.rawset(RPC_INVOKE, args -> engine.wrapString("req-late"));
            network.rawset(RPC_POLL, args -> answered.get()
                    ? readyState(engine, DONE, engine.wrapNumber(99))
                    : readyState(engine, PENDING, null));
            network.rawset(RPC_CANCEL, args -> engine.nilValue());
            engine.getGlobalEnvironment().rawset(NETWORK_SERVICE, network);
            engine.executeString(LATE_CALL_SCRIPT, "remote_function_wait");
            return engine;
        }

        @Test
        void remoteFunctionYieldsUntilTheResponseArrivesGetAndClearLastExecutionError() {
            AtomicBoolean answered = new AtomicBoolean();
            ILuaEngine engine = lateNetworkEngine(answered);
            assertNull(engine.getAndClearLastExecutionError(),
                    "scheduling the waiting call must not raise a Lua error");
        }

        @Test
        void remoteFunctionYieldsUntilTheResponseArrivesRpcPhase() {
            AtomicBoolean answered = new AtomicBoolean();
            ILuaEngine engine = lateNetworkEngine(answered);
            engine.callFunction(tickFunction(engine));
            assertEquals("waiting", engine.getGlobalEnvironment().rawget("_rpcPhase").asString(),
                    "the caller must be suspended while the request is in flight");
        }

        @Test
        void remoteFunctionYieldsUntilTheResponseArrivesRpcPhase2() {
            AtomicBoolean answered = new AtomicBoolean();
            ILuaEngine engine = lateNetworkEngine(answered);
            engine.callFunction(tickFunction(engine));
            answered.set(true);
            sleepForPollInterval();
            engine.callFunction(tickFunction(engine));
            assertEquals("done", engine.getGlobalEnvironment().rawget("_rpcPhase").asString(),
                    "the caller must resume once the answer arrives");
        }

        @Test
        void remoteFunctionYieldsUntilTheResponseArrivesRpcValue() {
            AtomicBoolean answered = new AtomicBoolean();
            ILuaEngine engine = lateNetworkEngine(answered);
            engine.callFunction(tickFunction(engine));
            answered.set(true);
            sleepForPollInterval();
            engine.callFunction(tickFunction(engine));
            assertEquals(99.0, engine.getGlobalEnvironment().rawget("_rpcValue").asDouble(),
                    "the resumed caller must receive the polled response value");
        }
    }

    @Nested
    class RemoteFunctionTimeout {

        private ILuaEngine timeoutNetworkEngine() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable network = engine.createTable();
            network.rawset(RPC_INVOKE, args -> engine.wrapString("req-never"));
            network.rawset(RPC_POLL, args -> readyState(engine, PENDING, null));
            network.rawset(RPC_CANCEL, args -> engine.nilValue());
            engine.getGlobalEnvironment().rawset(NETWORK_SERVICE, network);
            // Fast-forward the injected clock past the 10s RPC timeout.
            engine.executeString(TIMEOUT_CALL_SCRIPT, "remote_function_timeout");
            return engine;
        }

        @Test
        void remoteFunctionTimesOutWhenNoAnswerArrivesGetAndClearLastExecutionError() {
            ILuaEngine engine = timeoutNetworkEngine();
            assertNull(engine.getAndClearLastExecutionError(),
                    "the timeout is handled inside the spawned task and must not surface as an engine error");
        }

        @Test
        void remoteFunctionTimesOutWhenNoAnswerArrivesTimedOut() {
            ILuaEngine engine = timeoutNetworkEngine();
            engine.callFunction(tickFunction(engine));
            String message = engine.getGlobalEnvironment().rawget("_rpcTimeout").asString();
            assertTrue(message.contains("timed out"),
                    "the error must report the timeout: " + message);
        }
    }

    @Nested
    class WaitReentry {

        private ILuaEngine reentryEngine() {
            ILuaEngine engine = new LuavaEngine(false);
            // A Java binding that calls back into the engine, like every *Service binding does.
            ILuaTable service = engine.createTable();
            service.rawset("touch", args -> {
                engine.wrapNumber(1.0);
                return engine.wrapString("ok");
            });
            engine.getGlobalEnvironment().rawset("Svc", service);
            engine.executeString(WAIT_REENTRY_SCRIPT, "wait_reentry");
            engine.callFunction(tickFunction(engine));
            return engine;
        }

        @Test
        void taskWaitDoesNotStallTickAndCanReenterJavaGetAndClearLastExecutionError() {
            ILuaEngine engine = reentryEngine();
            AwaitSupport.awaitMillis(30);
            engine.callFunction(tickFunction(engine));
            assertNull(engine.getAndClearLastExecutionError(),
                    "calling Java from a coroutine and then waiting must not raise a Lua error");
        }

        @Test
        void taskWaitDoesNotStallTickAndCanReenterJavaPhase() {
            ILuaEngine engine = reentryEngine();
            assertEquals("start", engine.getGlobalEnvironment().rawget("_phase").asString(),
                    "the task must suspend at Task.wait");
        }

        @Test
        void taskWaitDoesNotStallTickAndCanReenterJavaJava() {
            ILuaEngine engine = reentryEngine();
            assertEquals("ok", engine.getGlobalEnvironment().rawget("_java").asString(),
                    "the task must be able to call Java bindings before waiting");
        }

        @Test
        void taskWaitDoesNotStallTickAndCanReenterJavaTaskWaitMustNotStallTheTickTook() {
            ILuaEngine engine = new LuavaEngine(false);
            ILuaTable service = engine.createTable();
            service.rawset("touch", args -> {
                engine.wrapNumber(1.0);
                return engine.wrapString("ok");
            });
            engine.getGlobalEnvironment().rawset("Svc", service);
            engine.executeString(WAIT_REENTRY_SCRIPT, "wait_reentry");
            long startNanos = System.nanoTime();
            engine.callFunction(tickFunction(engine));
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            AwaitSupport.awaitMillis(30);
            engine.callFunction(tickFunction(engine));
            // Before the execution-gate fix this tick blocked ~2s on the coroutine/engine monitor.
            assertTrue(elapsedMillis < 500L,
                    "Task.wait must not stall the tick, took " + elapsedMillis + " ms");
        }

        @Test
        void taskWaitDoesNotStallTickAndCanReenterJavaPhase2() {
            ILuaEngine engine = reentryEngine();
            AwaitSupport.awaitMillis(30);
            engine.callFunction(tickFunction(engine));
            assertEquals("done", engine.getGlobalEnvironment().rawget("_phase").asString(),
                    "the task must resume after its delay elapses");
        }

        @Test
        void taskWaitDoesNotStallTickAndCanReenterJavaGetAndClearLastExecutionError2() {
            ILuaEngine engine = reentryEngine();
            AwaitSupport.awaitMillis(30);
            engine.callFunction(tickFunction(engine));
            assertNull(engine.getAndClearLastExecutionError(),
                    "the reentry task must finish its wait without leaving an engine error");
        }
    }

    @Nested
    class OnceAndFireSync {

        private ILuaEngine onceFiredTwice() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(ONCE_SCRIPT, "once_twice");
            engine.callFunction(tickFunction(engine));
            return engine;
        }

        @Test
        void onceFiresOnlyOnceWhenSignalFiresTwiceBeforeTickGetAndClearLastExecutionError() {
            ILuaEngine engine = onceFiredTwice();
            assertNull(engine.getAndClearLastExecutionError(),
                    "firing a Once listener twice must not raise a Lua error");
        }

        @Test
        void onceFiresOnlyOnceWhenSignalFiresTwiceBeforeTickOnceCount() {
            ILuaEngine engine = onceFiredTwice();
            assertEquals(1.0, engine.getGlobalEnvironment().rawget("_onceCount").asDouble(),
                    "a Once listener must not run twice when Fire is called twice in one tick");
        }

        @Test
        void fireSyncRunsAllListenersWhenOneDisconnectsItselfGetAndClearLastExecutionError() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(FIRESYNC_DISCONNECT_SCRIPT, "firesync_disconnect");
            assertNull(engine.getAndClearLastExecutionError(),
                    "a self-disconnecting Once listener must not raise during FireSync");
        }

        @Test
        void fireSyncRunsAllListenersWhenOneDisconnectsItselfSyncRan() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(FIRESYNC_DISCONNECT_SCRIPT, "firesync_disconnect");
            assertEquals(11.0, engine.getGlobalEnvironment().rawget("_syncRan").asDouble(),
                    "a listener disconnecting itself must not skip the next listener");
        }
    }

    @Nested
    class Shutdown {

        private ILuaEngine suspendedThenShutdown() {
            ILuaEngine engine = new LuavaEngine(false);
            engine.executeString(SHUTDOWN_SCRIPT, "shutdown_task");
            engine.callFunction(tickFunction(engine));
            engine.shutdown();
            return engine;
        }

        @Test
        void shutdownStopsSuspendedTasksAndRejectsFurtherCallsAfterShutdown() {
            ILuaEngine engine = suspendedThenShutdown();
            assertTrue(engine.getGlobalEnvironment().rawget("_afterShutdown").isNil(),
                    "a suspended task must never resume after shutdown");
        }

        @Test
        void shutdownStopsSuspendedTasksAndRejectsFurtherCallsRejectsAShutDownEngineMustRejectFurtherExecutio() {
            ILuaEngine engine = suspendedThenShutdown();
            assertThrows(IllegalStateException.class, () -> engine.callFunction(tickFunction(engine)),
                    "a shut-down engine must reject further execution");
        }
    }
}
