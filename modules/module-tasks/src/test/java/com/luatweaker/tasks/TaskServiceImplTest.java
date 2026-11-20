package com.luatweaker.tasks;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaskServiceImplTest {

    private ILuaEngine engine;
    private TaskServiceImpl taskService;

    @BeforeEach
    public void setUp() {
        engine = LuaEngineFactory.create(true);
        taskService = new TaskServiceImpl();
        TaskLuaBinding.registerBindings(engine, taskService);
    }

    @Test
    public void testTaskServiceBindings() {
        ILuaTable global = engine.getGlobalEnvironment();
        ILuaTable taskTable = global.rawget("task").asTable();

        assertNotNull(taskTable, "Global task table should be registered");
        assertNotNull(taskTable.rawget("spawn"), "task.spawn should exist");
        assertNotNull(taskTable.rawget("delay"), "task.delay should exist");
        assertNotNull(taskTable.rawget("wait"), "task.wait should exist");
        assertNotNull(taskTable.rawget("_stats"), "task._stats should exist for /lt doctor");
    }

    @Test
    public void testTaskSpawnAndWaitScriptExecution() {
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeStringOrThrow(
                "task.spawn(function() _spawnedByTask = true end)\n" +
                "assert(_spawnedByTask == nil, 'spawn must be deferred, not run inline')",
                "TaskTest");

        ILuaValue tick = globals.rawget("task").asTable().rawget("_tick");
        assertNotNull(tick, "task._tick must exist");
        engine.callFunction(tick);
        assertTrue(globals.rawget("_spawnedByTask").asBoolean(),
                "the spawned task must run on the next tick");
    }

    @Test
    public void javaSchedulerRunsSpawnedCallbackOnTick() {
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeStringOrThrow("_javaRan = function(value) _javaValue = value end", "java_task_fn");
        ILuaValue fn = globals.rawget("_javaRan");

        taskService.spawn(engine, fn, new ILuaValue[]{engine.wrapNumber(7)});
        assertEquals(1, taskService.getQueuedTaskCount(), "spawn must queue the callback");

        taskService.tick(engine);

        assertEquals(0, taskService.getQueuedTaskCount(), "the queue must drain on tick");
        assertEquals(7.0, globals.rawget("_javaValue").asDouble());
        assertEquals(1, taskService.getLastTickExecuted());
        assertEquals(1, taskService.getExecutedTaskCount());
        assertFalse(globals.rawget("_javaValue").isNil());
    }

    @Test
    public void javaSchedulerDelayWaitsForItsDeadline() {
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeStringOrThrow("_delayedRan = function() _delayedFlag = true end", "java_delay_fn");
        ILuaValue fn = globals.rawget("_delayedRan");

        taskService.delay(engine, 0.25, fn, null);
        taskService.tick(engine);
        assertTrue(globals.rawget("_delayedFlag") == null || globals.rawget("_delayedFlag").isNil(),
                "a delayed callback must not run before its deadline");
        assertEquals(1, taskService.getQueuedTaskCount());

        sleep(300);
        taskService.tick(engine);
        assertTrue(globals.rawget("_delayedFlag").asBoolean());
        assertEquals(0, taskService.getQueuedTaskCount());
    }

    @Test
    public void javaSchedulerCancelRemovesTheCallback() {
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeStringOrThrow("_cancelled = function() _cancelRan = true end", "java_cancel_fn");
        ILuaValue fn = globals.rawget("_cancelled");

        taskService.spawn(engine, fn, null);
        taskService.cancel(fn);
        taskService.tick(engine);

        assertTrue(globals.rawget("_cancelRan") == null || globals.rawget("_cancelRan").isNil(),
                "a cancelled callback must not run");
        assertEquals(0, taskService.getQueuedTaskCount());
    }

    @Test
    public void javaSchedulerContainsCallbackErrors() {
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeStringOrThrow(
                "_failing = function() error('boom') end\n" +
                "_afterFailure = true",
                "java_error_fn");
        ILuaValue fn = globals.rawget("_failing");

        taskService.spawn(engine, fn, null);
        taskService.tick(engine);

        assertTrue(globals.rawget("_afterFailure").asBoolean(),
                "a failing callback must not abort the scheduler or the VM");
        assertEquals(0, taskService.getQueuedTaskCount());
    }

    @Test
    public void nonFunctionCallbacksAreIgnored() {
        taskService.spawn(engine, engine.wrapString("not a function"), null);
        assertEquals(0, taskService.getQueuedTaskCount());
    }

    @Test
    public void delayUsesTheMonotonicClock() {
        java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong(5_000_000_000L);
        ILuaEngine clockEngine = LuaEngineFactory.create(true);
        TaskServiceImpl service = new TaskServiceImpl(now::get);
        ILuaTable globals = clockEngine.getGlobalEnvironment();
        clockEngine.executeStringOrThrow("_clocked = function() _clockRan = true end", "clock_fn");
        ILuaValue fn = globals.rawget("_clocked");

        service.delay(clockEngine, 1.0, fn, null);
        service.tick(clockEngine);
        assertTrue(globals.rawget("_clockRan") == null || globals.rawget("_clockRan").isNil(),
                "a 1s callback must not run at the same instant it was scheduled");
        assertEquals(1, service.getQueuedTaskCount());

        now.addAndGet(1_100_000_000L);
        service.tick(clockEngine);
        assertTrue(globals.rawget("_clockRan").asBoolean());
        assertEquals(0, service.getQueuedTaskCount());
    }

    @Test
    public void cancelRemovesDelayedCallbacksAndKeepsTheCountAccurate() {
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeStringOrThrow("_delayedCancel = function() _delayedCancelRan = true end", "cancel_fn");
        ILuaValue fn = globals.rawget("_delayedCancel");

        taskService.delay(engine, 60.0, fn, null);
        assertEquals(1, taskService.getQueuedTaskCount());

        taskService.cancel(fn);
        assertEquals(0, taskService.getQueuedTaskCount(), "the counter must drop with the cancelled task");

        taskService.tick(engine);
        assertTrue(globals.rawget("_delayedCancelRan") == null
                        || globals.rawget("_delayedCancelRan").isNil(),
                "a cancelled delayed callback must not run");
    }

    @Test
    public void taskNowIsMonotonic() {
        ILuaTable taskTable = engine.getGlobalEnvironment().rawget("task").asTable();
        ILuaValue nowFn = taskTable.rawget("_now");
        assertNotNull(nowFn, "the scheduler needs a monotonic clock");
        assertTrue(nowFn.isFunction());

        double first = engine.callFunction(nowFn).asDouble();
        double second = engine.callFunction(nowFn).asDouble();
        assertTrue(second >= first, "monotonic clock must never go backwards: " + first + " -> " + second);
    }

    @Test
    public void perTickBudgetCarriesTasksOverAndKeepsCancellationWorking() {
        ILuaTable globals = engine.getGlobalEnvironment();
        engine.executeStringOrThrow(
                "task._maxPerTick = 2\n" +
                "_budgetRan = 0\n" +
                "_first = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n" +
                "_second = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n" +
                "_third = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n" +
                "_fourth = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n" +
                "_fifth = task.spawn(function() _budgetRan = _budgetRan + 1 end)",
                "budget_setup");
        assertNull(engine.getAndClearLastExecutionError());

        ILuaTable taskTable = globals.rawget("task").asTable();
        ILuaValue tick = taskTable.rawget("_tick");
        ILuaValue stats = taskTable.rawget("_stats");

        engine.callFunction(tick);
        assertEquals(2.0, globals.rawget("_budgetRan").asDouble(),
                "one tick must stop at the budget");
        assertEquals(3, engine.callFunction(stats).asTable().rawget("deferred").asInt(),
                "the rest must be carried over, not dropped");

        // A carried-over task has not started, so it can still be cancelled.
        engine.executeString("_cancelledCarry = task.cancel(_fifth)", "budget_cancel");
        assertTrue(globals.rawget("_cancelledCarry").asBoolean());

        engine.callFunction(tick);
        assertEquals(4.0, globals.rawget("_budgetRan").asDouble());
        engine.callFunction(tick);
        assertEquals(4.0, globals.rawget("_budgetRan").asDouble(),
                "the cancelled carried-over task must never run");
        assertEquals(0, engine.callFunction(stats).asTable().rawget("queued").asInt());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the delayed task: " + e.getMessage());
        }
    }
}
