package com.luatweaker.tasks;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Per-tick budget: tasks beyond the budget are carried over (never dropped) and a
 * carried-over task that has not started can still be cancelled.
 */
class TaskServiceImplBudgetTest {

    private static final String TASK_GLOBAL = "task";
    private static final String BUDGET_SETUP =
            "task._maxPerTick = 2\n"
            + "_budgetRan = 0\n"
            + "_first = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n"
            + "_second = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n"
            + "_third = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n"
            + "_fourth = task.spawn(function() _budgetRan = _budgetRan + 1 end)\n"
            + "_fifth = task.spawn(function() _budgetRan = _budgetRan + 1 end)";

    private ILuaEngine engine;
    private ILuaTable taskTable;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        TaskLuaBinding.registerBindings(engine, new TaskServiceImpl());
        engine.executeStringOrThrow(BUDGET_SETUP, "budget_setup");
        taskTable = engine.getGlobalEnvironment().rawget(TASK_GLOBAL).asTable();
    }

    @Test
    void perTickBudgetCarriesTasksOverAndKeepsCancellationWorking() {
        ILuaValue tick = taskTable.rawget("_tick");
        ILuaValue stats = taskTable.rawget("_stats");

        engine.callFunction(tick);
        double afterFirstTick = ranCount();
        int deferredAfterFirstTick = statsField(stats, "deferred");

        boolean cancelledCarry = false;
        engine.executeString("_cancelledCarry = task.cancel(_fifth)", "budget_cancel");
        ILuaValue cancelled = engine.getGlobalEnvironment().rawget("_cancelledCarry");
        if (cancelled != null && !cancelled.isNil()) {
            cancelledCarry = cancelled.asBoolean();
        }
        final boolean carryWasCancelled = cancelledCarry;
        engine.callFunction(tick);
        double afterSecondTick = ranCount();
        engine.callFunction(tick);
        double afterThirdTick = ranCount();
        int queuedAtEnd = statsField(stats, "queued");

        Assertions.assertAll("the per-tick budget must throttle without dropping tasks",
            () -> Assertions.assertEquals(2.0, afterFirstTick,
                "one tick must stop at the configured budget of two tasks"),
            () -> Assertions.assertEquals(3, deferredAfterFirstTick,
                "the remaining tasks must be carried over, not dropped"),
            () -> Assertions.assertTrue(carryWasCancelled,
                "a carried-over task has not started, so task.cancel must still succeed"),
            () -> Assertions.assertEquals(4.0, afterSecondTick,
                "the next tick must run the carried-over tasks"),
            () -> Assertions.assertEquals(4.0, afterThirdTick,
                "the cancelled carried-over task must never run"),
            () -> Assertions.assertEquals(0, queuedAtEnd,
                "the queue must be empty once every task has run or been cancelled"));
    }

    private double ranCount() {
        return engine.getGlobalEnvironment().rawget("_budgetRan").asDouble();
    }

    private int statsField(ILuaValue stats, String field) {
        return engine.callFunction(stats).asTable().rawget(field).asInt();
    }
}
