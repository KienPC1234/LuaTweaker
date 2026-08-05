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
 * at this shared bus. A listener registered by a NEWER engine REPLACES all
 * listeners of OLDER engines (the most recently loaded mod's listeners win), while
 * multiple listeners from the SAME engine may all fire — e.g. two modules of one
 * mod listening to the same "ServerTick" event must both run.</p>
 */
public class EventServiceImpl implements IEventService {
    public record ListenerEntry(ILuaEngine engine, ILuaValue function) {}

    private static final Map<String, java.util.List<ListenerEntry>> LISTENERS = new ConcurrentHashMap<>();

    private final ILuaEngine engine;

    public EventServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    @NotNull
    public ILuaEngine getEngine() {
        return engine;
    }

    @Override
    public void listen(@NotNull String eventName, @NotNull Object callback) {
        if (callback instanceof ILuaValue lv && lv.isFunction()) {
            java.util.List<ListenerEntry> entries = LISTENERS.computeIfAbsent(
                    eventName, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
            // Older-engine listeners are stale (their closures belong to a previous
            // load cycle); drop them so the newest engine wins. Same-engine listeners
            // are kept: multiple modules may listen to one event.
            entries.removeIf(e -> !e.engine().equals(this.engine));
            entries.add(new ListenerEntry(this.engine, lv));
        }
    }

    @Override
    public void post(@NotNull String eventName, @NotNull ILuaTable payload) {
        fireEvent(eventName, payload);
    }

    @Override
    public void fireEvent(@NotNull String eventName, @NotNull ILuaTable payload) {
        java.util.List<ListenerEntry> entries = LISTENERS.get(eventName);
        if (entries == null) return;
        for (ListenerEntry entry : entries) {
            if (entry.function() == null || !entry.function().isFunction()) continue;
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

    public void fireRawEvent(@NotNull String eventName, @NotNull ILuaValue payload) {
        java.util.List<ListenerEntry> entries = LISTENERS.get(eventName);
        if (entries == null) return;
        for (ListenerEntry entry : entries) {
            if (entry.function() == null || !entry.function().isFunction()) continue;
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

    public static void fireTeardownHooks() {
        java.util.List<ListenerEntry> entries = LISTENERS.get(com.luatweaker.api.event.EventNames.ON_SCRIPT_UNLOAD);
        if (entries != null) {
            for (ListenerEntry entry : entries) {
                if (entry.function() != null && entry.function().isFunction()) {
                    try {
                        entry.engine().callFunction(entry.function());
                    } catch (Exception e) {
                        com.luatweaker.api.log.LuaTweakerLog.get().error(
                            com.luatweaker.api.log.LogStage.RUNTIME_ERROR,
                            "Error in OnScriptUnload hook: " + e.getMessage()
                        );
                    }
                }
            }
        }
        LISTENERS.clear();
    }
}
