package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts Java return values into {@link ILuaValue}s for {@link LuaBinder}-generated bindings.
 *
 * <p>Companion of {@code LuaArgConverter} for the opposite direction: Java values
 * flow back to Lua through this class. Custom conversions are registered per
 * return type via {@link LuaBinder#registerReturnConverter} and stored here.</p>
 */
final class LuaResultConverter {
    private LuaResultConverter() {}

    private static final Map<Class<?>, LuaBinder.ValueConverter> RETURN_CONVERTERS =
        new ConcurrentHashMap<>();

    /** Registers a custom converter used when a bound method returns {@code type}. */
    static void registerReturnConverter(@NotNull Class<?> type, @NotNull LuaBinder.ValueConverter converter) {
        RETURN_CONVERTERS.put(type, converter);
    }

    /**
     * Converts a Java return value for the declared return type.
     *
     * <p>Maps and lists become Lua tables (never userdata) so scripts can index
     * them directly (e.g. {@code Loot:GetModifications()["mob"]}).</p>
     */
    static @NotNull ILuaValue toLua(@NotNull ILuaEngine engine, @NotNull Class<?> returnType,
            Object result) {
        if (result == null || returnType == void.class || returnType == Void.class) {
            return engine.nilValue();
        }
        ILuaValue scalar = toLuaScalar(engine, result);
        if (scalar != null) {
            return scalar;
        }
        if (result instanceof Map<?, ?> map) {
            return toLuaMap(engine, map);
        }
        if (result instanceof List<?> list) {
            return toLuaList(engine, list);
        }
        LuaBinder.ValueConverter converter = findConverter(returnType);
        if (converter == null) {
            converter = findConverter(result.getClass());
        }
        if (converter != null) {
            return converter.toLua(engine, result);
        }
        return engine.wrapUserdata(result);
    }

    /**
     * Finds the most specific converter for {@code type} via breadth-first search
     * over direct interfaces and superclasses so sub-interfaces (such as {@code IPlayer}
     * extending {@code IEntity}) win cleanly over broader parent converters.
     */
    private static @Nullable LuaBinder.ValueConverter findConverter(@Nullable Class<?> type) {
        if (type == null) {
            return null;
        }
        LuaBinder.ValueConverter exact = RETURN_CONVERTERS.get(type);
        if (exact != null || RETURN_CONVERTERS.isEmpty()) {
            return exact;
        }
        Queue<Class<?>> queue = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        queue.add(type);
        visited.add(type);
        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            LuaBinder.ValueConverter hit = RETURN_CONVERTERS.get(current);
            if (hit != null) {
                return hit;
            }
            enqueueHierarchy(current, queue, visited);
        }
        return null;
    }

    private static void enqueueHierarchy(Class<?> current, Queue<Class<?>> queue, Set<Class<?>> visited) {
        for (Class<?> iface : current.getInterfaces()) {
            if (visited.add(iface)) {
                queue.add(iface);
            }
        }
        Class<?> parent = current.getSuperclass();
        if (parent != null && visited.add(parent)) {
            queue.add(parent);
        }
    }

    /**
     * @return the Lua scalar for Java scalars and already-wrapped values,
     *         or {@code null} for maps, lists and userdata.
     */
    private static @Nullable ILuaValue toLuaScalar(@NotNull ILuaEngine engine, @NotNull Object result) {
        if (result instanceof ILuaValue luaValue) {
            return luaValue;
        }
        if (result instanceof String text) {
            return engine.wrapString(text);
        }
        if (result instanceof Boolean flag) {
            return engine.wrapBoolean(flag);
        }
        if (result instanceof Number number) {
            return engine.wrapNumber(number.doubleValue());
        }
        return null;
    }

    private static @NotNull ILuaTable toLuaMap(@NotNull ILuaEngine engine, @NotNull Map<?, ?> map) {
        ILuaTable table = engine.createTable();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            table.rawset(String.valueOf(entry.getKey()),
                toLua(engine, entry.getValue() == null ? Object.class : entry.getValue().getClass(),
                    entry.getValue()));
        }
        return table;
    }

    private static @NotNull ILuaTable toLuaList(@NotNull ILuaEngine engine, @NotNull List<?> list) {
        ILuaTable table = engine.createTable();
        int index = 1;
        for (Object item : list) {
            table.rawset(index++,
                toLua(engine, item == null ? Object.class : item.getClass(), item));
        }
        return table;
    }
}
