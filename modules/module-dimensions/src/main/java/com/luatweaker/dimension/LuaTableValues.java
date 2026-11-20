package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/**
 * Typed readers for Lua config tables.
 *
 * <p>Both the dimension config parser and the terrain spec parser read the same table shapes,
 * so the type checks and error wording live here once instead of being copied per parser.</p>
 */
final class LuaTableValues {

    private LuaTableValues() {}

    static boolean bool(ILuaTable t, String key, boolean def) {
        ILuaValue v = t.rawget(key);
        return v != null && !v.isNil() ? v.asBoolean() : def;
    }

    static double number(ILuaTable t, String key, double def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return def;
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number, got: " + describe(v));
        }
        return v.asDouble();
    }

    static String string(ILuaTable t, String key, String def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return def;
        if (!v.isString()) {
            throw new IllegalArgumentException(key + " must be a string, got: " + describe(v));
        }
        return v.asString();
    }

    static Long optionalLong(ILuaTable t, String key) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return null;
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number or nil, got: " + describe(v));
        }
        return (long) Math.floor(v.asDouble());
    }

    static Integer optionalInt(ILuaTable t, String key) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return null;
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number or nil, got: " + describe(v));
        }
        return (int) Math.floor(v.asDouble());
    }

    static String describe(ILuaValue v) {
        return v == null ? "nil" : v.getClass().getSimpleName();
    }

    /** Minecraft resource-location charset: [a-z0-9_.-] for path, optional [a-z0-9_.-] namespace. */
    static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    static boolean isResourceLocation(String id) {
        return id != null && RESOURCE_LOCATION.matcher(id).matches();
    }
}
