package com.luatweaker.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link ILuaValue} to JSON element tree mapping used by {@link JsonServiceImpl}.
 *
 * <p>Tables are encoded as JSON arrays when their keys are exactly {@code 1..len},
 * otherwise as JSON objects; cycles are rejected through the identity set owned by
 * the caller.</p>
 */
final class LuaToJsonEncoder {
    private LuaToJsonEncoder() {}

    static @NotNull JsonElement luaToJson(@Nullable ILuaValue value, @NotNull Set<Object> visited) {
        if (value == null || value.isNil()) {
            return JsonNull.INSTANCE;
        }
        if (value.isBoolean()) {
            return new JsonPrimitive(value.asBoolean());
        }
        if (value.isNumber()) {
            return jsonNumber(value.asDouble());
        }
        if (value.isString()) {
            return new JsonPrimitive(value.asString());
        }
        if (value.isTable()) {
            return tableToJson(value, visited);
        }
        if (value.isUserdata()) {
            return userdataToJson(value);
        }
        return JsonNull.INSTANCE;
    }

    private static @NotNull JsonElement tableToJson(@NotNull ILuaValue value, @NotNull Set<Object> visited) {
        ILuaTable table = value.asTable();
        // asTable() allocates a fresh wrapper per call, so identity must be taken from
        // the underlying table object or a cycle looks like two different tables.
        Object identity = table.toJavaObject();
        if (identity == null) {
            identity = table;
        }
        if (!visited.add(identity)) {
            throw new IllegalArgumentException("Json.Encode failed: circular table reference detected");
        }
        try {
            return encodeTable(table, visited);
        } finally {
            visited.remove(identity);
        }
    }

    private static @NotNull JsonElement userdataToJson(@NotNull ILuaValue value) {
        Object obj = value.toJavaObject();
        if (obj == null) {
            return JsonNull.INSTANCE;
        }
        return new JsonPrimitive(obj.toString());
    }

    /** Encodes a Lua number, keeping integral values on the integer path. */
    private static @NotNull JsonPrimitive jsonNumber(double value) {
        long asLong = (long) value;
        if (value == asLong && !Double.isInfinite(value)) {
            return new JsonPrimitive(asLong);
        }
        return new JsonPrimitive(value);
    }

    private static @NotNull JsonElement encodeTable(@NotNull ILuaTable table, @NotNull Set<Object> visited) {
        int len = table.length();
        if (isContiguousArray(table, len)) {
            JsonArray arr = new JsonArray(len);
            for (int i = 1; i <= len; i++) {
                ILuaValue item = table.rawget(i);
                arr.add(luaToJson(item, visited));
            }
            return arr;
        }
        return encodeObject(table, visited);
    }

    /** True for tables with exactly the keys {@code 1..len} (mixed tables stay objects). */
    private static boolean isContiguousArray(@NotNull ILuaTable table, int len) {
        if (len <= 0) {
            return false;
        }
        Set<Long> indices = new HashSet<>();
        int[] total = {0};
        table.forEach((key, value) -> {
            total[0]++;
            if (key.isNumber()) {
                double kd = key.asDouble();
                long kl = (long) kd;
                if (kd == kl && kl >= 1 && kl <= len) {
                    indices.add(kl);
                }
            }
        });
        return total[0] == len && indices.size() == len;
    }

    /** Encodes a table as a JSON object (or an empty table as {@code {}}). */
    private static @NotNull JsonObject encodeObject(@NotNull ILuaTable table, @NotNull Set<Object> visited) {
        JsonObject obj = new JsonObject();
        table.forEach((k, v) -> {
            if (v == null || v.isNil()) {
                return;
            }
            obj.add(luaKey(k), luaToJson(v, visited));
        });
        return obj;
    }

    /** Renders a Lua key as a JSON member name, keeping integral numbers compact. */
    private static @NotNull String luaKey(@NotNull ILuaValue key) {
        if (key.isString()) {
            return key.asString();
        }
        if (key.isNumber()) {
            double kd = key.asDouble();
            long kl = (long) kd;
            return kd == kl ? String.valueOf(kl) : String.valueOf(kd);
        }
        return String.valueOf(key.toJavaObject());
    }
}
