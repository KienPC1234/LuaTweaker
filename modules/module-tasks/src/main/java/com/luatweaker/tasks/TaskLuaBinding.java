package com.luatweaker.tasks;

import com.luatweaker.api.task.ITaskService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TaskLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ITaskService taskService) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaTable taskTable;

        ILuaValue existingTask = globals.rawget("task");
        if (existingTask != null && !existingTask.isNil() && existingTask.isTable()) {
            taskTable = existingTask.asTable();
            taskTable.rawset("getTimeClock", args -> engine.wrapNumber(System.currentTimeMillis() / 1000.0));
        } else {
            taskTable = engine.createTable();
        }

        taskTable.rawset("_java_tick", args -> {
            taskService.tick(engine);
            return engine.nilValue();
        });

        globals.rawset("task", taskTable);
        engine.registerService("TaskService", taskTable);
        engine.registerService("Task", taskTable);
    }
}
