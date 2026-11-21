package com.luatweaker.tasks;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cancellation semantics and the monotonic clock exposed as {@code task._now}.
 */
class TaskServiceImplCancelTest {

    private static final String TASK_GLOBAL = "task";

    private ILuaEngine engine;
    private TaskServiceImpl service;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        service = new TaskServiceImpl();
        TaskLuaBinding.registerBindings(engine, service);
    }

    @Test
    void cancelRemovesASpawnedCallback() {
        engine.executeStringOrThrow("_cancelled = function() _cancelRan = true end", "java_cancel_fn");
        ILuaValue function = globals().rawget("_cancelled");
        service.spawn(engine, function, null);

        service.cancel(function);
        service.tick(engine);

        Assertions.assertTrue(isUnset("_cancelRan"),
                "a cancelled spawned callback must never run");
    }

    @Test
    void cancelRemovesADelayedCallback() {
        engine.executeStringOrThrow("_delayedCancel = function() _delayedCancelRan = true end", "cancel_fn");
        ILuaValue function = globals().rawget("_delayedCancel");
        service.delay(engine, 60.0, function, null);

        service.cancel(function);
        service.tick(engine);

        Assertions.assertTrue(isUnset("_delayedCancelRan"),
                "a cancelled delayed callback must never run");
    }

    @Test
    void cancelUpdatesTheQueuedCount() {
        engine.executeStringOrThrow("_delayedCancel = function() end", "cancel_count_fn");
        ILuaValue function = globals().rawget("_delayedCancel");
        service.delay(engine, 60.0, function, null);

        service.cancel(function);

        Assertions.assertEquals(0, service.getQueuedTaskCount(),
                "the queued counter must drop when its task is cancelled");
    }

    @Test
    void taskNowIsMonotonic() {
        ILuaValue nowFunction = globals().rawget(TASK_GLOBAL).asTable().rawget("_now");
        double first = engine.callFunction(nowFunction).asDouble();
        double second = engine.callFunction(nowFunction).asDouble();

        Assertions.assertAll("task._now must be a monotonic function",
            () -> Assertions.assertNotNull(nowFunction, "the scheduler needs a monotonic clock"),
            () -> Assertions.assertTrue(nowFunction.isFunction(), "task._now must be a Lua function"),
            () -> Assertions.assertTrue(second >= first,
                "the monotonic clock must never go backwards: " + first + " -> " + second));
    }

    private ILuaTable globals() {
        return engine.getGlobalEnvironment();
    }

    private boolean isUnset(String name) {
        ILuaValue value = globals().rawget(name);
        return value == null || value.isNil();
    }
}
