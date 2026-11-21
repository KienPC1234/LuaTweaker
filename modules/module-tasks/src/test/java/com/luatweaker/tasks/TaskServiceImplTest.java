package com.luatweaker.tasks;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Task table surface and the Java-side spawn/tick scheduler.
 */
class TaskServiceImplTest {

    private static final String TASK_GLOBAL = "task";
    private static final String TICK_FUNCTION = "_tick";

    private ILuaEngine engine;
    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        taskService = new TaskServiceImpl();
        TaskLuaBinding.registerBindings(engine, taskService);
    }

    @Test
    void taskTableExposesSpawnDelayWaitAndStats() {
        ILuaTable taskTable = taskTable();

        Assertions.assertAll("the task table must expose the scheduler entry points",
            () -> Assertions.assertNotNull(taskTable.rawget("spawn"), "task.spawn must exist"),
            () -> Assertions.assertNotNull(taskTable.rawget("delay"), "task.delay must exist"),
            () -> Assertions.assertNotNull(taskTable.rawget("wait"), "task.wait must exist"),
            () -> Assertions.assertNotNull(taskTable.rawget("_stats"),
                "task._stats must exist for /lt doctor"));
    }

    @Test
    void spawnDefersTheCallbackUntilTheNextTick() {
        engine.executeString(
                "task.spawn(function() _spawnedByTask = true end)\n"
                + "assert(_spawnedByTask == nil, 'spawn must be deferred, not run inline')",
                "TaskTest");

        Assertions.assertNull(engine.getAndClearLastExecutionError(),
                "task.spawn must not execute the callback inline");
    }

    @Test
    void spawnedCallbackRunsOnTheNextTick() {
        engine.executeString("task.spawn(function() _spawnedByTask = true end)", "TaskTest");

        engine.callFunction(taskTable().rawget(TICK_FUNCTION));

        Assertions.assertTrue(globals().rawget("_spawnedByTask").asBoolean(),
                "the spawned task must run on the next tick");
    }

    @Test
    void javaSpawnQueuesTheCallbackWithoutRunningIt() {
        engine.executeStringOrThrow("_javaRan = function(value) _javaValue = value end", "java_task_fn");

        taskService.spawn(engine, globals().rawget("_javaRan"), new ILuaValue[] {engine.wrapNumber(7)});

        Assertions.assertEquals(1, taskService.getQueuedTaskCount(),
                "spawn must queue the callback instead of running it immediately");
    }

    @Test
    void javaTickRunsTheQueuedCallbackWithItsArgumentsAndUpdatesCounters() {        engine.executeStringOrThrow("_javaRan = function(value) _javaValue = value end", "java_task_fn");
        taskService.spawn(engine, globals().rawget("_javaRan"), new ILuaValue[] {engine.wrapNumber(7)});

        taskService.tick(engine);

        ILuaValue value = globals().rawget("_javaValue");
        Assertions.assertAll("one tick must run the callback exactly once",
            () -> Assertions.assertEquals(0, taskService.getQueuedTaskCount(),
                "the queue must drain on tick"),
            () -> Assertions.assertEquals(7.0, value.asDouble(),
                "the callback must receive the wrapped argument"),
            () -> Assertions.assertEquals(1, taskService.getLastTickExecuted(),
                "the tick counter must advance by one"),
            () -> Assertions.assertEquals(1, taskService.getExecutedTaskCount(),
                "the executed task counter must count the callback"));
    }

    @Test
    void javaSchedulerContainsCallbackErrors() {
        engine.executeStringOrThrow(
                "_failing = function() error('boom') end\n"
                + "_afterFailure = true",
                "java_error_fn");
        taskService.spawn(engine, globals().rawget("_failing"), null);

        taskService.tick(engine);

        ILuaValue afterFailure = globals().rawget("_afterFailure");
        Assertions.assertAll("a failing callback must not abort the scheduler",
            () -> Assertions.assertTrue(afterFailure != null && afterFailure.asBoolean(),
                "the script must continue after the callback error"),
            () -> Assertions.assertEquals(0, taskService.getQueuedTaskCount(),
                "the failing callback must still be removed from the queue"));
    }

    @Test
    void nonFunctionCallbacksAreIgnored() {
        taskService.spawn(engine, engine.wrapString("not a function"), null);

        Assertions.assertEquals(0, taskService.getQueuedTaskCount(),
                "spawning a non-function value must not queue anything");
    }

    private ILuaTable globals() {
        return engine.getGlobalEnvironment();
    }
    private ILuaTable taskTable() {
        return globals().rawget(TASK_GLOBAL).asTable();
    }
}
