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
        ILuaTable taskTable = engine.createTable();

        taskTable.rawset("spawn", args -> {
            if (args.length >= 2) {
                ILuaValue fn = args[1];
                ILuaValue[] fnArgs = Arrays.copyOfRange(args, 2, args.length);
                taskService.spawn(engine, fn, fnArgs);
            }
            return engine.nilValue();
        });

        taskTable.rawset("delay", args -> {
            if (args.length >= 3) {
                double sec = args[1].asDouble();
                ILuaValue fn = args[2];
                ILuaValue[] fnArgs = Arrays.copyOfRange(args, 3, args.length);
                taskService.delay(engine, sec, fn, fnArgs);
            }
            return engine.nilValue();
        });

        taskTable.rawset("defer", args -> {
            if (args.length >= 2) {
                ILuaValue fn = args[1];
                ILuaValue[] fnArgs = Arrays.copyOfRange(args, 2, args.length);
                taskService.defer(engine, fn, fnArgs);
            }
            return engine.nilValue();
        });

        taskTable.rawset("cancel", args -> {
            if (args.length >= 2) {
                taskService.cancel(args[1]);
            }
            return engine.nilValue();
        });

        taskTable.rawset("_tick", args -> {
            taskService.tick(engine);
            return engine.nilValue();
        });

        globals.rawset("task", taskTable);
        engine.registerService("TaskService", taskService);
    }
}
