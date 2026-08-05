package com.luatweaker.tasks;

import com.luatweaker.api.task.ITaskService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TaskServiceImpl implements ITaskService {

    private static record DelayedTask(long triggerTimeMs, ILuaValue task, ILuaValue[] args) {}

    private final List<DelayedTask> delayedTasks = new CopyOnWriteArrayList<>();
    private final List<DelayedTask> deferredTasks = new CopyOnWriteArrayList<>();

    @Override
    public void spawn(@NotNull ILuaEngine engine, @NotNull ILuaValue task, ILuaValue[] args) {
        if (task.isFunction()) {
            deferredTasks.add(new DelayedTask(0, task, args != null ? args : new ILuaValue[0]));
        }
    }

    @Override
    public void delay(@NotNull ILuaEngine engine, double seconds, @NotNull ILuaValue task, ILuaValue[] args) {
        long triggerMs = System.currentTimeMillis() + (long)(seconds * 1000.0);
        delayedTasks.add(new DelayedTask(triggerMs, task, args != null ? args : new ILuaValue[0]));
    }

    @Override
    public void defer(@NotNull ILuaEngine engine, @NotNull ILuaValue task, ILuaValue[] args) {
        deferredTasks.add(new DelayedTask(0, task, args != null ? args : new ILuaValue[0]));
    }

    @Override
    public void cancel(@NotNull ILuaValue thread) {
        deferredTasks.removeIf(dt -> dt.task().equals(thread));
        delayedTasks.removeIf(dt -> dt.task().equals(thread));
    }

    @Override
    public void tick(@NotNull ILuaEngine engine) {
        // 1. Process deferred tasks
        if (!deferredTasks.isEmpty()) {
            List<DelayedTask> copy = new ArrayList<>(deferredTasks);
            deferredTasks.clear();
            for (DelayedTask dt : copy) {
                if (dt.task().isFunction()) {
                    try {
                        engine.callFunction(dt.task(), dt.args());
                    } catch (RuntimeException re) {
                        com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RUNTIME_ERROR, "LuaError executing deferred task: " + re.getMessage());
                    } catch (Exception e) {
                        java.io.StringWriter sw = new java.io.StringWriter();
                        e.printStackTrace(new java.io.PrintWriter(sw));
                        com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RUNTIME_ERROR, "Critical Error executing deferred task: " + e.getMessage() + "\n" + sw.toString());
                    }
                }
            }
        }

        // 2. Process delayed tasks whose time has arrived
        if (!delayedTasks.isEmpty()) {
            long now = System.currentTimeMillis();
            List<DelayedTask> expired = new ArrayList<>();
            for (DelayedTask dt : delayedTasks) {
                if (now >= dt.triggerTimeMs()) {
                    expired.add(dt);
                }
            }
            if (!expired.isEmpty()) {
                delayedTasks.removeAll(expired);
                for (DelayedTask dt : expired) {
                    if (dt.task().isFunction()) {
                        try {
                            engine.callFunction(dt.task(), dt.args());
                        } catch (RuntimeException re) {
                            com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RUNTIME_ERROR, "LuaError executing delayed task: " + re.getMessage());
                        } catch (Exception e) {
                            java.io.StringWriter sw = new java.io.StringWriter();
                            e.printStackTrace(new java.io.PrintWriter(sw));
                            com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RUNTIME_ERROR, "Critical Error executing delayed task: " + e.getMessage() + "\n" + sw.toString());
                        }
                    }
                }
            }
        }
    }
}
