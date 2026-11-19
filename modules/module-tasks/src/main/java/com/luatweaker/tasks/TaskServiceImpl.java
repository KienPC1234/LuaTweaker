package com.luatweaker.tasks;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.task.ITaskService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Java-side scheduler for Lua callbacks, ticked by {@code task._java_tick}. The bootstrap
 * {@code task.spawn}/{@code task.delay} Lua implementation is the primary scheduler; this
 * service exists for Java code that needs to enqueue a Lua callback without re-entering the
 * Lua scheduler, and it surfaces counters for {@code /lt doctor}.
 */
public class TaskServiceImpl implements ITaskService {

    private static final int MAX_QUEUED_TASKS = 10_000;
    private static final int MAX_TASKS_PER_TICK = 1_000;
    private static final long SLOW_TASK_NANOS = 25_000_000L;

    private final AtomicLong droppedTasks = new AtomicLong();
    private final AtomicLong slowTasks = new AtomicLong();
    private final AtomicLong executedTasks = new AtomicLong();
    private final Queue<PendingTask> deferredTasks = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<PendingTask> delayedTasks =
            new PriorityBlockingQueue<>(11, Comparator.comparingLong(PendingTask::triggerTimeMs));
    private volatile int lastTickExecuted;

    private record PendingTask(long triggerTimeMs, ILuaValue task, ILuaValue[] args) {
    }

    @Override
    public void spawn(@NotNull ILuaEngine engine, @NotNull ILuaValue task, @Nullable ILuaValue[] args) {
        if (task.isFunction()) {
            enqueue(deferredTasks, new PendingTask(0L, task, args));
        }
    }

    @Override
    public void delay(@NotNull ILuaEngine engine, double seconds, @NotNull ILuaValue task,
                      @Nullable ILuaValue[] args) {
        if (task.isFunction()) {
            long triggerAt = System.currentTimeMillis() + Math.max(0L, (long) (seconds * 1000.0));
            enqueue(delayedTasks, new PendingTask(triggerAt, task, args));
        }
    }

    @Override
    public void defer(@NotNull ILuaEngine engine, @NotNull ILuaValue task, @Nullable ILuaValue[] args) {
        if (task.isFunction()) {
            enqueue(deferredTasks, new PendingTask(0L, task, args));
        }
    }

    @Override
    public void cancel(@NotNull ILuaValue task) {
        deferredTasks.removeIf(pending -> pending.task().equals(task));
        delayedTasks.removeIf(pending -> pending.task().equals(task));
    }

    @Override
    public void tick(@NotNull ILuaEngine engine) {
        int executed = 0;
        PendingTask pending;
        while (executed < MAX_TASKS_PER_TICK && (pending = deferredTasks.poll()) != null) {
            execute(engine, pending, "deferred");
            executed++;
        }
        long now = System.currentTimeMillis();
        while (executed < MAX_TASKS_PER_TICK
                && (pending = delayedTasks.peek()) != null
                && now >= pending.triggerTimeMs()) {
            delayedTasks.poll();
            execute(engine, pending, "delayed");
            executed++;
        }
        lastTickExecuted = executed;
    }

    public int getQueuedTaskCount() {
        return deferredTasks.size() + delayedTasks.size();
    }

    public long getDroppedTaskCount() {
        return droppedTasks.get();
    }

    public long getSlowTaskCount() {
        return slowTasks.get();
    }

    public long getExecutedTaskCount() {
        return executedTasks.get();
    }

    public int getLastTickExecuted() {
        return lastTickExecuted;
    }

    private void enqueue(Queue<PendingTask> queue, PendingTask task) {
        if (getQueuedTaskCount() >= MAX_QUEUED_TASKS) {
            droppedTasks.incrementAndGet();
            LuaTweakerLog.get().warn(LogStage.RUNTIME_ERROR,
                    "Lua task queue limit reached (" + MAX_QUEUED_TASKS + "); dropping new task");
            return;
        }
        queue.add(task);
    }

    private void execute(ILuaEngine engine, PendingTask pending, String kind) {
        long startedAt = System.nanoTime();
        try {
            runAsCoroutine(engine, pending);
            executedTasks.incrementAndGet();
        } catch (RuntimeException e) {
            LuaTweakerLog.get().error(LogStage.RUNTIME_ERROR,
                    "Lua " + kind + " task failed: "
                            + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            long elapsed = System.nanoTime() - startedAt;
            if (elapsed > SLOW_TASK_NANOS) {
                slowTasks.incrementAndGet();
                LuaTweakerLog.get().warn(LogStage.RUNTIME_ERROR,
                        "Slow Lua " + kind + " task: " + elapsed / 1_000_000L + "ms");
            }
        }
    }

    /**
     * Runs the callback through {@code task._run_deferred} so it gets a real coroutine (and can
     * therefore call {@code task.wait}); falls back to a direct call on an engine without it.
     */
    private void runAsCoroutine(ILuaEngine engine, PendingTask pending) {
        ILuaValue[] args = pending.args() == null ? new ILuaValue[0] : pending.args();
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue taskTable = globals.rawget("task");
        if (taskTable != null && taskTable.isTable()) {
            ILuaValue runner = taskTable.asTable().rawget("_run_deferred");
            if (runner != null && runner.isFunction()) {
                ILuaTable argsTable = engine.createTable();
                for (int i = 0; i < args.length; i++) {
                    argsTable.rawset(i + 1, args[i]);
                }
                engine.callFunction(runner, pending.task(), argsTable);
                return;
            }
        }
        engine.callFunction(pending.task(), args);
    }
}
