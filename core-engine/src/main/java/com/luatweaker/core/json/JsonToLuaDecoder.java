package com.luatweaker.core.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * JSON element tree to {@link ILuaValue} mapping used by {@link JsonServiceImpl}.
 *
 * <p>Kept separate from the Lua-to-JSON direction so each conversion policy lives
 * in one place: this class allocates tables and primitives through the target
 * engine, while {@link LuaToJsonEncoder} reads Lua values.</p>
 */
final class JsonToLuaDecoder {
    private JsonToLuaDecoder() {}

    static @NotNull ILuaValue jsonToLua(@NotNull ILuaEngine engine, @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return engine.nilValue();
        }
        if (element.isJsonPrimitive()) {
            return primitiveToLua(engine, element.getAsJsonPrimitive());
        }
        if (element.isJsonArray()) {
            return arrayToLua(engine, element.getAsJsonArray());
        }
        if (element.isJsonObject()) {
            return objectToLua(engine, element.getAsJsonObject());
        }
        return engine.nilValue();
    }

    private static @NotNull ILuaValue primitiveToLua(@NotNull ILuaEngine engine, @NotNull JsonPrimitive prim) {
        if (prim.isBoolean()) {
            return engine.wrapBoolean(prim.getAsBoolean());
        }
        if (prim.isNumber()) {
            return wrapJsonNumber(engine, prim.getAsNumber().doubleValue());
        }
        if (prim.isString()) {
            return engine.wrapString(prim.getAsString());
        }
        return engine.nilValue();
    }

    private static @NotNull ILuaTable arrayToLua(@NotNull ILuaEngine engine, @NotNull JsonArray arr) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < arr.size(); i++) {
            table.rawset(i + 1, jsonToLua(engine, arr.get(i)));
        }
        return table;
    }

    private static @NotNull ILuaTable objectToLua(@NotNull ILuaEngine engine, @NotNull JsonObject obj) {
        ILuaTable table = engine.createTable();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            table.rawset(entry.getKey(), jsonToLua(engine, entry.getValue()));
        }
        return table;
    }

    /** Wraps a JSON number, keeping integral values on the integer path. */
    private static @NotNull ILuaValue wrapJsonNumber(@NotNull ILuaEngine engine, double value) {
        long asLong = (long) value;
        if (value == asLong && !Double.isInfinite(value)) {
            return engine.wrapNumber(asLong);
        }
        return engine.wrapNumber(value);
    }
}
