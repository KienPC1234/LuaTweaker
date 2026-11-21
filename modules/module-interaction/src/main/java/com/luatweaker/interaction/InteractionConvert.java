package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Converts Java Map/List/primitive shapes (NbtCodec shape) to and from Lua values. */
final class InteractionConvert {

    private InteractionConvert() {
    }

    /** Recursively converts a Java Map/List/primitive (NbtCodec shape) to a Lua table/value. */
    @NotNull
    static ILuaValue wrapValue(@NotNull ILuaEngine engine, @Nullable Object value) {
        if (value instanceof String s) {
            return engine.wrapString(s);
        }
        if (value instanceof Boolean b) {
            return engine.wrapBoolean(b);
        }
        if (value instanceof Number n) {
            return engine.wrapNumber(n.doubleValue());
        }
        if (value instanceof byte[] bytes) {
            return wrapArray(engine, bytes.length, i -> engine.wrapNumber(bytes[i] & 0xFF));
        }
        if (value instanceof int[] ints) {
            return wrapArray(engine, ints.length, i -> engine.wrapNumber(ints[i]));
        }
        if (value instanceof long[] longs) {
            return wrapArray(engine, longs.length, i -> engine.wrapNumber(longs[i]));
        }
        if (value instanceof java.util.List<?> list) {
            return wrapList(engine, list);
        }
        if (value instanceof java.util.Map<?, ?> map) {
            return wrapMap(engine, map);
        }
        return engine.nilValue();
    }

    private static ILuaValue wrapList(ILuaEngine engine, java.util.List<?> list) {
        ILuaTable listTable = engine.createTable();
        for (int i = 0; i < list.size(); i++) {
            listTable.rawset(i + 1, wrapValue(engine, list.get(i)));
        }
        return listTable;
    }

    private static ILuaValue wrapMap(ILuaEngine engine, java.util.Map<?, ?> map) {
        ILuaTable mapTable = engine.createTable();
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            mapTable.rawset(String.valueOf(entry.getKey()), wrapValue(engine, entry.getValue()));
        }
        return mapTable;
    }

    private static ILuaValue wrapArray(ILuaEngine engine, int length,
            java.util.function.IntFunction<ILuaValue> reader) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < length; i++) {
            table.rawset(i + 1, reader.apply(i));
        }
        return table;
    }

    /** Recursively converts a Lua table/value to a Java Map (NbtCodec shape). */
    @NotNull
    static java.util.Map<String, Object> toJavaMap(@NotNull ILuaTable table) {
        java.util.Map<String, Object> map = new java.util.concurrent.ConcurrentHashMap<>();
        for (java.util.Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
            map.put(String.valueOf(entry.getKey().toJavaObject()), toJavaValue(entry.getValue()));
        }
        return map;
    }

    static Object toJavaValue(ILuaValue value) {
        if (value.isTable()) {
            return toJavaTableValue(value.asTable());
        }
        Object raw = value.toJavaObject();
        if (raw instanceof String || raw instanceof Boolean || raw instanceof Number) {
            return raw;
        }
        if (value.isNil()) {
            return null;
        }
        return raw;
    }

    /**
     * Lua tables come in two shapes that must map to different NBT forms:
     * array-like tables (integer keys 1..n, e.g. NBT lists such as {@code Items})
     * become {@code List}s, everything else becomes a {@code Map}.
     */
    private static Object toJavaTableValue(ILuaTable table) {
        java.util.Map<ILuaValue, ILuaValue> entries = table.asMap();
        if (!entries.isEmpty() && isArrayLike(entries)) {
            Object[] ordered = new Object[entries.size()];
            boolean placedAny = false;
            for (java.util.Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
                int index = ((Number) entry.getKey().toJavaObject()).intValue();
                if (index >= 1 && index <= ordered.length) {
                    ordered[index - 1] = toJavaValue(entry.getValue());
                    placedAny = true;
                }
            }
            if (placedAny) {
                java.util.List<Object> list = new java.util.ArrayList<>(ordered.length);
                list.addAll(java.util.Arrays.asList(ordered));
                return list;
            }
        }
        return toJavaMap(table);
    }

    private static boolean isArrayLike(java.util.Map<ILuaValue, ILuaValue> entries) {
        for (ILuaValue key : entries.keySet()) {
            if (!(key.toJavaObject() instanceof Number num) || num.intValue() < 1) {
                return false;
            }
        }
        return true;
    }
}
