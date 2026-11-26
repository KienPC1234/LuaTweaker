package com.luatweaker.events;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.luava.runtime.LuaException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    private static final Map<String, List<ListenerEntry>> LISTENERS = new ConcurrentHashMap<>();

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
            List<ListenerEntry> entries = LISTENERS.computeIfAbsent(
                    eventName, k -> new CopyOnWriteArrayList<>());
            // Older-engine listeners are stale (their closures belong to a previous
            // load cycle); drop them so the newest engine wins. Same-engine listeners
            // are kept: multiple modules may listen to one event.
            entries.removeIf(e -> !e.engine().equals(this.engine));
            entries.add(new ListenerEntry(this.engine, lv));
            return;
        }
        LuaTweakerLog.get().warn(
                LogStage.SYSTEM,
                "Events:Listen('" + eventName + "') ignored a "
                        + (callback == null ? "null" : callback.getClass().getSimpleName())
                        + " callback; a Lua function is required");
    }

    @Override
    public void post(@NotNull String eventName, @NotNull ILuaTable payload) {
        fireEvent(eventName, payload);
    }

    /**
     * Hot-path guard: lets high-frequency forwarders (tick, damage, spawn)
     * skip payload construction when nothing listens. A {@code false} here
     * matches what {@link #fireRawEvent} would return for zero listeners.
     */
    public boolean hasListeners(@NotNull String eventName) {
        List<ListenerEntry> entries = LISTENERS.get(eventName);
        return entries != null && !entries.isEmpty();
    }

    @Override
    public void fireEvent(@NotNull String eventName, @NotNull ILuaTable payload) {
        List<ListenerEntry> entries = LISTENERS.get(eventName);
        if (entries == null) {
            return;
        }
        for (ListenerEntry entry : entries) {
            if (!isCallable(entry)) {
                continue;
            }
            invokeListener(eventName, entry, payload);
        }
    }

    @Override
    public boolean fireCancellable(@NotNull String eventName, @NotNull ILuaTable payload) {
        return dispatchCancellable(eventName, payload);
    }

    public boolean fireRawEvent(@NotNull String eventName, @NotNull ILuaValue payload) {
        return dispatchCancellable(eventName, payload);
    }

    private boolean dispatchCancellable(@NotNull String eventName, @NotNull ILuaValue payload) {
        List<ListenerEntry> entries = LISTENERS.get(eventName);
        if (entries == null) {
            return true;
        }
        boolean cancelled = false;
        for (ListenerEntry entry : entries) {
            if (!isCallable(entry)) {
                continue;
            }
            cancelled = invokeListener(eventName, entry, payload) || cancelled;
        }
        return !cancelled;
    }

    public static void fireTeardownHooks() {
        List<ListenerEntry> entries = LISTENERS.get(com.luatweaker.api.event.EventNames.ON_SCRIPT_UNLOAD);
        if (entries != null) {
            for (ListenerEntry entry : entries) {
                if (entry.function() != null && entry.function().isFunction()) {
                    try {
                        entry.engine().callFunction(entry.function());
                    } catch (LuaException e) {
                        LuaTweakerLog.get().error(
                            LogStage.RUNTIME_ERROR,
                            "Error in OnScriptUnload hook: " + e.getMessage()
                        );
                    }
                }
            }
        }
        LISTENERS.clear();
    }

    /**
     * Invokes one listener, logging (not propagating) Lua failures so one broken
     * listener cannot abort the remaining listeners or the game tick.
     *
     * @return true when the listener returned Lua {@code false} (cancels the event)
     */
    private static boolean invokeListener(@NotNull String eventName, @NotNull ListenerEntry entry,
                                          @NotNull ILuaValue payload) {
        try {
            ILuaValue result = entry.engine().callFunction(entry.function(), payload);
            // A listener returning Lua `false` cancels the event.
            return result != null && result.isBoolean() && !result.asBoolean();
        } catch (LuaException e) {
            logListenerError(eventName, e);
            return false;
        }
    }

    private static boolean isCallable(@NotNull ListenerEntry entry) {
        return entry.function() != null && entry.function().isFunction();
    }

    private static void logListenerError(@NotNull String eventName, @NotNull LuaException e) {
        StringWriter sw = new StringWriter();
        try (PrintWriter printer = new PrintWriter(sw)) {
            e.printStackTrace(printer);
        }
        LuaTweakerLog.get().error(
            LogStage.RUNTIME_ERROR,
            "Error invoking event listener for " + eventName + ": " + e.getMessage() + "\n" + sw.toString()
        );
    }
}
