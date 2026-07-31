package com.luatweaker.api.event;

import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

public interface IEventService {
    void listen(@NotNull String eventName, @NotNull Object callback);
    void post(@NotNull String eventName, @NotNull ILuaTable payload);
    void fireEvent(@NotNull String eventName, @NotNull ILuaTable payload);
}
