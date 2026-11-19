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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted while waiting for the delayed task: " + e.getMessage());
        }
    }
}
