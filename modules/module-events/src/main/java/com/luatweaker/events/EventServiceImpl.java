package com.luatweaker.events;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventServiceImpl implements IEventService {
    public record ListenerEntry(ILuaEngine engine, ILuaValue function) {}

    private static final Map<String, List<ListenerEntry>> LISTENERS = new ConcurrentHashMap<>();

    private final ILuaEngine engine;

    public EventServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    public static void clear() {
        LISTENERS.clear();
    }

    @Override
    public void listen(@NotNull String eventName, @NotNull Object callback) {
        if (callback instanceof ILuaValue lv && lv.isFunction()) {
            LISTENERS.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>())
                    .add(new ListenerEntry(this.engine, lv));
        }
    }

    @Override
    public void post(@NotNull String eventName, @NotNull ILuaTable payload) {
        fireEvent(eventName, payload);
    }

    @Override
    public void fireEvent(@NotNull String eventName, @NotNull ILuaTable payload) {
        List<ListenerEntry> list = LISTENERS.get(eventName);
        if (list != null) {
            for (ListenerEntry entry : list) {
                if (entry.function() != null && entry.function().isFunction()) {
                    try {
                        entry.engine().callFunction(entry.function(), payload);
                    } catch (Exception e) {
                        java.io.StringWriter sw = new java.io.StringWriter();
                        e.printStackTrace(new java.io.PrintWriter(sw));
                        com.luatweaker.api.log.LuaTweakerLog.get().error(
                            com.luatweaker.api.log.LogStage.RUNTIME_ERROR,
                            "Error invoking event listener for " + eventName + ": " + e.getMessage() + "\n" + sw.toString()
                        );
                    }
                }
            }
        }
    }
}
