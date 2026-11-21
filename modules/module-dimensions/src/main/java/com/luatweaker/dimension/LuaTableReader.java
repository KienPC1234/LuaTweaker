package com.luatweaker.dimension;

import com.google.gson.JsonObject;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Mechanical Lua-table readers shared by the biome section parsers.
 * Every method throws {@link IllegalArgumentException} on shape mismatch.
 */
final class LuaTableReader {

    private LuaTableReader() {
    }

    static ILuaTable requireTable(@Nullable ILuaValue value, @NotNull String what) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException(what + " must be a table");
        }
        return value.asTable();
    }

    static String reqString(@NotNull ILuaTable t, @NotNull String key, @NotNull String what) {
        ILuaValue v = t.rawget(key);
        if (v == null || !v.isString()) {
            throw new IllegalArgumentException(what + " is required and must be a string");
        }
        return v.asString();
    }

    static double reqNumber(@NotNull ILuaTable t, @NotNull String key, @NotNull String what) {
        ILuaValue v = t.rawget(key);
        if (v == null || !v.isNumber()) {
            throw new IllegalArgumentException(what + " is required and must be a number");
        }
        return v.asDouble();
    }

    static boolean bool(@NotNull ILuaTable t, @NotNull String key, boolean def) {
        ILuaValue v = t.rawget(key);
        if (v != null && !v.isNil()) {
            return v.asBoolean();
        }
        return def;
    }

    static double finite(@NotNull ILuaTable t, @NotNull String key, double def, @NotNull String what) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isNumber() || !Double.isFinite(v.asDouble())) {
            throw new IllegalArgumentException(what + " must be a finite number");
        }
        return v.asDouble();
    }

    static double range(@NotNull ILuaTable t, @NotNull String key, double def,
            double min, double max, @NotNull String what) {
        double value = finite(t, key, def, what);
        if (value < min || value > max) {
            throw new IllegalArgumentException(what + " must be between " + min + " and " + max + ", got: " + value);
        }
        return value;
    }

    static void optColor(@NotNull ILuaTable t, @NotNull String key, @NotNull JsonObject effects) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return;
        }
        if (!v.isNumber()) {
            throw new IllegalArgumentException("effects." + key + " must be an RGB number 0x000000-0xFFFFFF");
        }
        int color = (int) v.asDouble();
        if (color < 0 || color > 0xFFFFFF) {
            throw new IllegalArgumentException("effects." + key + " must be an RGB number 0x000000-0xFFFFFF, got: " + color);
        }
        effects.addProperty(key, color);
    }
}
