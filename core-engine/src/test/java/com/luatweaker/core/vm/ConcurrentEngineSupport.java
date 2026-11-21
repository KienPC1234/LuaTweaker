package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shared concurrent-VM scenarios. The thread model under test is a single shared
 * VM whose entry points serialize cleanly: a "render" thread running scripts and a
 * "server" thread invoking Lua functions must produce no crashes and no lost updates.
 *
 * <p>Workers run as {@link TimerTask}s on daemon timers (never raw threads) and
 * every worker outcome is drained through {@link FutureTask#get}, so any failure —
 * including VM errors — is recorded instead of failing silently.
 */
final class ConcurrentEngineSupport {

    static final int ITERATIONS = 200;

    private ConcurrentEngineSupport() {
    }

    record EngineRun(ILuaEngine engine, ILuaValue incrementFn, String setupError,
            boolean finished, Throwable failure) {
    }

    private record Outcome(boolean finished, Throwable failure) {
    }

    private static TimerTask deferredWork(CountDownLatch done, FutureTask<?> task) {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    task.run();
                } finally {
                    done.countDown();
                }
            }
        };
    }

    private static Outcome awaitOutcome(CountDownLatch done, FutureTask<?> task) {
        boolean finished;
        try {
            finished = done.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            return new Outcome(false, interrupted);
        }
        if (!finished) {
            return new Outcome(false, new TimeoutException("worker threads did not finish in 30s"));
        }
        try {
            task.get(1, TimeUnit.SECONDS);
            return new Outcome(true, null);
        } catch (ExecutionException exploded) {
            return new Outcome(true, exploded.getCause());
        } catch (InterruptedException | TimeoutException e) {
            return new Outcome(false, e);
        }
    }

    private static EngineRun runScenario(ILuaEngine engine, ILuaValue incrementFn, String setupError,
            FutureTask<?> first, FutureTask<?> second) {
        CountDownLatch done = new CountDownLatch(2);
        Timer timer = new Timer(true);
        try {
            timer.schedule(deferredWork(done, first), 0);
            timer.schedule(deferredWork(done, second), 0);
            Outcome firstOutcome = awaitOutcome(done, first);
            Outcome secondOutcome = awaitOutcome(done, second);
            Throwable failure = firstOutcome.failure() != null
                    ? firstOutcome.failure() : secondOutcome.failure();
            return new EngineRun(engine, incrementFn, setupError,
                    firstOutcome.finished() && secondOutcome.finished(), failure);
        } finally {
            timer.cancel();
        }
    }

    static EngineRun runInterleavedScenario() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeString(
                "_concurrentCounter = 0\n"
                        + "function _concurrentIncrement(amount)\n"
                        + "    _concurrentCounter = _concurrentCounter + amount\n"
                        + "    return _concurrentCounter\n"
                        + "end",
                "concurrent_setup");
        String setupError = engine.getAndClearLastExecutionError();
        ILuaValue incrementFn = globals.rawget("_concurrentIncrement");

        FutureTask<?> scriptWork = new FutureTask<>(() -> {
            for (int i = 1; i <= ITERATIONS; i++) {
                engine.executeString(
                        "_concurrentFromJava = " + i + "\n"
                                + "assert(type(_concurrentIncrement) == 'function', 'setup must remain visible')",
                        "concurrent_write_" + i);
            }
        }, null);
        FutureTask<?> functionWork = new FutureTask<>(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                ILuaValue[] results = engine.callFunctionMulti(incrementFn, engine.wrapNumber(1));
                if (results.length != 1 || results[0].asInt() != i + 1) {
                    throw new AssertionError(
                            "unexpected increment result at iteration " + i + ": "
                                    + (results.length == 0 ? "<none>" : results[0].asString()));
                }
            }
        }, null);
        return runScenario(engine, incrementFn, setupError, scriptWork, functionWork);
    }

    static EngineRun runSharedCounterScenario() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeString(
                "_sharedCounter = 0\n"
                        + "function _incrementShared() _sharedCounter = _sharedCounter + 1 return _sharedCounter end",
                "concurrent_setup_shared");
        String setupError = engine.getAndClearLastExecutionError();
        ILuaValue incrementFn = globals.rawget("_incrementShared");

        Runnable incrementLoop = () -> {
            for (int i = 0; i < ITERATIONS; i++) {
                engine.callFunction(incrementFn);
            }
        };
        return runScenario(engine, incrementFn, setupError,
                new FutureTask<>(incrementLoop, null), new FutureTask<>(incrementLoop, null));
    }
}
