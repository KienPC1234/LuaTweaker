package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Event-class to payload-builder registry. Keeps {@link EventPayloadBuilder} a thin dispatcher
 * instead of one god class that references every NeoForge event type.
 */
final class PayloadRegistry {

    private final Map<Class<? extends Event>, BiFunction<ILuaEngine, Event, ILuaTable>>
            handlers = new ConcurrentHashMap<>();

    /** Looks up the builder registered for the event's exact runtime class. */
    @Nullable ILuaTable build(@NotNull ILuaEngine engine, @NotNull Event event) {
        BiFunction<ILuaEngine, Event, ILuaTable> handler = handlers.get(event.getClass());
        return handler == null ? null : handler.apply(engine, event);
    }

    <T extends Event> void add(@NotNull Class<T> eventClass, @NotNull BiFunction<ILuaEngine, T, ILuaTable> builder) {
        handlers.put(eventClass, (engine, event) -> builder.apply(engine, eventClass.cast(event)));
    }
}
