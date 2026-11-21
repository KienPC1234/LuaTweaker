package com.luatweaker.tasks;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.task.ITaskService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

import java.util.Comparator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Java-side scheduler for Lua callbacks, ticked by {@code task._java_tick}. The bootstrap
 * {@code task.spawn}/{@code task.delay} Lua implementation is the primary scheduler; this
 * service exists for Java code that needs to enqueue a Lua callback without re-entering the
 * Lua scheduler, and it surfaces counters for {@code /lt doctor}.
 *
 * <p>Deadlines use {@link System#nanoTime()} (monotonic), not the wall clock: an NTP
 * correction or a user changing the system clock must not delay, advance or strand a
 * scheduled callback.</p>
 */
public class TaskServiceImpl implements ITaskService {

    private static final int MAX_QUEUED_TASKS = 10_000;
    private static final int MAX_TASKS_PER_TICK = 1_000;
    private static final long SLOW_TASK_NANOS = 25_000_000L;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final LongSupplier nanoClock;
    private final AtomicLong droppedTasks = new AtomicLong();
    private final AtomicLong slowTasks = new AtomicLong();
    private final AtomicLong executedTasks = new AtomicLong();
    private final Queue<PendingTask> deferredTasks = new ConcurrentLinkedQueue<>();
    private final BlockingQueue<PendingTask> delayedTasks =
            new PriorityBlockingQueue<>(11, Comparator.comparingLong(PendingTask::triggerAtNanos));
    /**
     * Maintained by hand because {@link ConcurrentLinkedQueue#size()} walks the whole queue;
     * calling it on every enqueue made a full queue quadratic.
     */
    private final AtomicInteger queuedTasks = new AtomicInteger();
    private final AtomicInteger lastTickExecuted = new AtomicInteger();

    private record PendingTask(long triggerAtNanos, ILuaValue task, ILuaValue[] args) {
    }

    public TaskServiceImpl() {
        this(System::nanoTime);
    }

    /** Injectable clock for tests and for platforms with a different monotonic source. */
    TaskServiceImpl(@NotNull LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
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
            long delayNanos = 0L;
            if (Double.isFinite(seconds) && seconds > 0.0) {
                delayNanos = (long) Math.min(seconds * NANOS_PER_SECOND, Long.MAX_VALUE / 4.0);
            }
            enqueue(delayedTasks, new PendingTask(nanoClock.getAsLong() + delayNanos, task, args));
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
        int removed = removeMatching(deferredTasks, task) + removeMatching(delayedTasks, task);
        if (removed > 0) {
            queuedTasks.addAndGet(-removed);
        }
    }

    @Override
    public void tick(@NotNull ILuaEngine engine) {
        int executed = 0;
        PendingTask pending;
        while (executed < MAX_TASKS_PER_TICK) {
            pending = deferredTasks.poll();
            if (pending == null) {
                break;
            }
            queuedTasks.decrementAndGet();
            execute(engine, pending, "deferred");
            executed++;
        }
        while (executed < MAX_TASKS_PER_TICK) {
            PendingTask head = delayedTasks.peek();
            if (head == null || nanoClock.getAsLong() < head.triggerAtNanos()) {
                break;
            }
            PendingTask due = delayedTasks.poll();
            if (due == null) {
                break;
            }
            // Another thread may have inserted an earlier deadline between peek and poll;
            // put it back instead of running a callback before its time.
            if (nanoClock.getAsLong() < due.triggerAtNanos()) {
                delayedTasks.add(due);
                break;
            }
            queuedTasks.decrementAndGet();
            execute(engine, due, "delayed");
            executed++;
        }
        lastTickExecuted.set(executed);
    }

    public int getQueuedTaskCount() {
        return queuedTasks.get();
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
        return lastTickExecuted.get();
    }

    private void enqueue(Queue<PendingTask> queue, PendingTask task) {
        if (queuedTasks.get() >= MAX_QUEUED_TASKS) {
            droppedTasks.incrementAndGet();
            LuaTweakerLog.get().warn(LogStage.RUNTIME_ERROR,
                    "Lua task queue limit reached (" + MAX_QUEUED_TASKS + "); dropping new task");
            return;
        }
        // Count first: both queues are unbounded, so add() cannot fail, and the tick loop can
        // never poll a task that the counter does not know about yet.
        queuedTasks.incrementAndGet();
        queue.add(task);
    }

    private static int removeMatching(Queue<PendingTask> queue, ILuaValue task) {
        int[] removed = {0};
        queue.removeIf(pending -> {
            if (pending.task().equals(task)) {
                removed[0]++;
                return true;
            }
            return false;
        });
        return removed[0];
    }

    private void execute(ILuaEngine engine, PendingTask pending, String kind) {
        long startedAt = System.nanoTime();
        try {
            runAsCoroutine(engine, pending);
            executedTasks.incrementAndGet();
        } catch (LuaException e) {
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
