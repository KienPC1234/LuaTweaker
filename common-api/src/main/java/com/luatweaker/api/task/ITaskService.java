package com.luatweaker.api.task;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

public interface ITaskService {
    void spawn(@NotNull ILuaEngine engine, @NotNull ILuaValue task, ILuaValue[] args);
    void delay(@NotNull ILuaEngine engine, double seconds, @NotNull ILuaValue task, ILuaValue[] args);
    void defer(@NotNull ILuaEngine engine, @NotNull ILuaValue task, ILuaValue[] args);
    void cancel(@NotNull ILuaValue thread);
    void tick(@NotNull ILuaEngine engine);
}
