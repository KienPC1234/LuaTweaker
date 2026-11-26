package com.luatweaker.core.bind;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Converts Lua arguments into Java values for {@link LuaBinder}-generated bindings.
 *
 * <p>Extracted from {@code LuaBinder} so each direction of the bridge has one home:
 * arguments flow through this class, return values through
 * {@code LuaResultConverter}, orchestration stays in {@code LuaBinder}.</p>
 */
final class LuaArgConverter {
    private LuaArgConverter() {}

    /** Parsers for {@code @LuaDefault} raw strings, keyed by exact parameter type. */
    private static final Map<Class<?>, Function<String, Object>> DEFAULT_PARSERS = Map.ofEntries(
        Map.entry(String.class, def -> def),
        Map.entry(CharSequence.class, def -> def),
        Map.entry(int.class, Integer::parseInt),
        Map.entry(Integer.class, Integer::valueOf),
        Map.entry(long.class, Long::parseLong),
        Map.entry(Long.class, Long::valueOf),
        Map.entry(double.class, Double::parseDouble),
        Map.entry(Double.class, Double::valueOf),
        Map.entry(float.class, Float::parseFloat),
        Map.entry(Float.class, Float::valueOf),
        Map.entry(boolean.class, Boolean::parseBoolean),
        Map.entry(Boolean.class, Boolean::valueOf),
        Map.entry(ILuaValue.class, def -> null),
        Map.entry(ILuaTable.class, def -> null),
        Map.entry(Map.class, def -> Map.of()),
        Map.entry(List.class, def -> List.of()),
        Map.entry(Object.class, def -> null),
        Map.entry(IEntity.class, def -> null)
    );

    /** Scalar converters keyed by exact parameter type; missing keys use the table/object path. */
    private static final Map<Class<?>, Function<ILuaValue, Object>> SCALAR_CONVERTERS = Map.ofEntries(
        Map.entry(String.class, ILuaValue::asString),
        Map.entry(CharSequence.class, ILuaValue::asString),
        Map.entry(int.class, ILuaValue::asInt),
        Map.entry(Integer.class, ILuaValue::asInt),
        Map.entry(long.class, value -> (long) value.asDouble()),
        Map.entry(Long.class, value -> (long) value.asDouble()),
        Map.entry(double.class, ILuaValue::asDouble),
        Map.entry(Double.class, ILuaValue::asDouble),
        Map.entry(float.class, value -> (float) value.asDouble()),
        Map.entry(Float.class, value -> (float) value.asDouble()),
        Map.entry(boolean.class, ILuaValue::asBoolean),
        Map.entry(Boolean.class, ILuaValue::asBoolean),
        Map.entry(ILuaValue.class, value -> value),
        Map.entry(ILuaTable.class, ILuaValue::asTable),
        Map.entry(Object.class, value -> value)
    );

    /**
     * Converts one Lua argument to the declared Java parameter type.
     *
     * @param type target Java type
     * @param arg  Lua value (never {@code null} from bound calls)
     * @return converted Java value, possibly {@code null} for entity lookups that miss
     */
    static @Nullable Object convertArg(@NotNull Class<?> type, @NotNull ILuaValue arg) {
        Function<ILuaValue, Object> scalar = SCALAR_CONVERTERS.get(type);
        if (scalar != null) {
            return scalar.apply(arg);
        }
        if (type.isArray() && arg.isTable()) {
            return convertArray(type.getComponentType(), arg.asTable());
        }
        if (List.class.isAssignableFrom(type) && arg.isTable()) {
            return convertList(arg.asTable());
        }
        if (Map.class.isAssignableFrom(type) && arg.isTable()) {
            return convertMap(arg.asTable());
        }
        if (IEntity.class.isAssignableFrom(type)) {
            return unwrapEntity(arg);
        }
        return arg.toJavaObject();
    }

    /** Array parameters (e.g. {@code String[] biomes}) arrive as Lua tables. */
    private static @NotNull Object convertArray(@NotNull Class<?> componentType, @NotNull ILuaTable tbl) {
        int len = tbl.length();
        Object array = Array.newInstance(componentType, len);
        for (int i = 1; i <= len; i++) {
            ILuaValue item = tbl.rawget(i);
            if (item == null || item.isNil()) {
                break;
            }
            Array.set(array, i - 1, convertArg(componentType, item));
        }
        return array;
    }

    /** List parameters arrive as Lua tables; elements are converted to plain Java objects. */
    private static @NotNull List<Object> convertList(@NotNull ILuaTable tbl) {
        int len = tbl.length();
        List<Object> list = new ArrayList<>(len);
        for (int i = 1; i <= len; i++) {
            ILuaValue item = tbl.rawget(i);
            if (item == null || item.isNil()) {
                break;
            }
            list.add(item.toJavaObject());
        }
        return list;
    }

    /** Sentinel indicating a malformed {@code @LuaDefault} that must fail lazily at invocation. */
    static final Object INVALID_DEFAULT = new Object();

    /** Map parameters arrive as Lua tables. */
    private static @NotNull Map<String, Object> convertMap(@NotNull ILuaTable tbl) {
        Map<String, Object> map = new ConcurrentHashMap<>();
        tbl.forEach((key, value) -> map.put(key.asString(), value.toJavaObject()));
        return map;
    }

    /** Unwraps an {@link IEntity} from a Lua entity table ({@code __entity}). */
    private static @Nullable IEntity unwrapEntity(@NotNull ILuaValue arg) {
        if (!arg.isTable()) {
            return null;
        }
        ILuaValue inner = arg.asTable().rawget("__entity");
        if (inner == null || inner.isNil()) {
            return null;
        }
        Object obj = inner.toJavaObject();
        return obj instanceof IEntity entity ? entity : null;
    }

    /**
     * Parses a raw {@code @LuaDefault} value into the declared parameter type.
     *
     * @return parsed value, or {@code null} for Lua-passthrough and entity types
     * @throws IllegalArgumentException when a numeric default is malformed
     */
    static @Nullable Object parseDefault(@NotNull String def, @NotNull Class<?> type,
            @NotNull String methodName, int index) {
        Function<String, Object> parser = DEFAULT_PARSERS.get(type);
        if (parser == null) {
            return null;
        }
        try {
            return parser.apply(def);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid @LuaDefault for " + methodName + " argument " + index + ": " + def, e);
        }
    }

    /** Pre-parses a {@code @LuaDefault} at bind time, returning {@link #INVALID_DEFAULT} if malformed. */
    static @Nullable Object tryPreparseDefault(@NotNull String def, @NotNull Class<?> type) {
        Function<String, Object> parser = DEFAULT_PARSERS.get(type);
        if (parser == null) {
            return null;
        }
        try {
            return parser.apply(def);
        } catch (NumberFormatException e) {
            return INVALID_DEFAULT;
        }
    }
}
