package com.luatweaker.events;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event bus shared across ALL Lua engines (startup, reloads, client).
 *
 * <p>Item/block interaction callbacks are created once on the startup engine, so
 * their Lua closures dispatch through the startup engine's {@code Events} table.
 * To route those dispatches to the CURRENT runtime, every engine's service points
 * at this shared bus and {@link #listen} REPLACES the previous listener for an
 * event: the most recently loaded mod's listener (bound to the newest engine) wins.
 */
public class EventServiceImpl implements IEventService {
    public record ListenerEntry(ILuaEngine engine, ILuaValue function) {}

    private static final Map<String, ListenerEntry> LISTENERS = new ConcurrentHashMap<>();

    private final ILuaEngine engine;

    public EventServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void listen(@NotNull String eventName, @NotNull Object callback) {
        if (callback instanceof ILuaValue lv && lv.isFunction()) {
            LISTENERS.put(eventName, new ListenerEntry(this.engine, lv));
        }
    }

    @Override
    public void post(@NotNull String eventName, @NotNull ILuaTable payload) {
        fireEvent(eventName, payload);
    }

    @Override
    public void fireEvent(@NotNull String eventName, @NotNull ILuaTable payload) {
        ListenerEntry entry = LISTENERS.get(eventName);
        if (entry != null && entry.function() != null && entry.function().isFunction()) {
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
