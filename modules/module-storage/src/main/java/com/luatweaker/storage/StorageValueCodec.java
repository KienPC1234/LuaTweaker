package com.luatweaker.storage;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates and normalizes Lua/Java values into the storable subset (null,
 * booleans, numbers, strings, and maps/lists of those). Anything else
 * (functions, userdata, threads) is rejected loudly instead of corrupting a
 * datapack file or silently vanishing.
 */
final class StorageValueCodec {

    private StorageValueCodec() {}

    static Object toStorableValue(String key, Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof String || val instanceof Number || val instanceof Boolean) {
            return val;
        }
        if (val instanceof ILuaValue lv) {
            return storableFromLuaValue(key, lv);
        }
        if (val instanceof Map<?, ?> map) {
            return storableFromMap(key, map);
        }
        if (val instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toStorableValue(key, item));
            }
            return Collections.unmodifiableList(out);
        }
        return requireScalar(key, val);
    }

    private static Object storableFromLuaValue(String key, ILuaValue lv) {
        if (lv.isNil()) {
            return null;
        }
        if (lv.isBoolean()) {
            return lv.asBoolean();
        }
        if (lv.isString()) {
            return lv.asString();
        }
        if (lv.isNumber()) {
            return lv.asDouble();
        }
        if (lv.isTable()) {
            return tableToStorable(key, lv.asTable());
        }
        return requireScalar(key, lv.toJavaObject());
    }

    private static Object storableFromMap(String key, Map<?, ?> map) {
        Map<String, Object> out = new ConcurrentHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object child = toStorableValue(key, entry.getValue());
            if (child != null) {
                out.put(String.valueOf(entry.getKey()), child);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    static Object tableToStorable(String key, ILuaTable table) {
        int length = table.length();
        Map<ILuaValue, ILuaValue> entries = table.asMap();
        if (isDenseArray(length, entries)) {
            return tableArrayValues(key, table, length);
        }
        Map<String, Object> out = new ConcurrentHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
            Object child = toStorableValue(key, entry.getValue());
            if (child != null) {
                out.put(entry.getKey().asString(), child);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static boolean isDenseArray(int length, Map<ILuaValue, ILuaValue> entries) {
        if (length <= 0 || entries.size() != length) {
            return false;
        }
        for (ILuaValue entryKey : entries.keySet()) {
            if (!entryKey.isNumber()) {
                return false;
            }
            int index = entryKey.asInt();
            if (index < 1 || index > length) {
                return false;
            }
        }
        return true;
    }

    private static List<Object> tableArrayValues(String key, ILuaTable table, int length) {
        List<Object> out = new ArrayList<>(length);
        for (int i = 1; i <= length; i++) {
            out.add(toStorableValue(key, table.rawget(i)));
        }
        return Collections.unmodifiableList(out);
    }

    static Object requireScalar(String key, Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        throw new IllegalArgumentException(
                "Storage key '" + key + "' only accepts booleans, numbers, strings and tables of "
                        + "those values; got " + (value == null ? "null" : value.getClass().getName()));
    }

    static double requireNumber(String key, Object existing) {
        if (existing == null) {
            return 0.0;
        }
        if (existing instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException("Storage key '" + key + "' holds a "
                + existing.getClass().getSimpleName()
                + ", not a number; IncrementAsync would corrupt the stored value");
    }

    @NotNull
    static List<String> sortedKeys(Set<String> keys) {
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        return sorted;
    }
}
