package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

/**
 * Typed readers for Lua config tables.
 *
 * <p>Both the dimension config parser and the terrain spec parser read the same table shapes,
 * so the type checks and error wording live here once instead of being copied per parser.</p>
 */
final class LuaTableValues {

    private LuaTableValues() {}

    static boolean booleanOr(ILuaTable t, String key, boolean def) {
        ILuaValue v = t.rawget(key);
        if (v != null && !v.isNil()) {
            return v.asBoolean();
        }
        return def;
    }

    static String describe(@Nullable ILuaValue value) {
        return value == null ? "nil" : value.getClass().getSimpleName();
    }

    static double number(ILuaTable t, String key, double def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number, got: " + describe(v));
        }
        return v.asDouble();
    }

    static String string(ILuaTable t, String key, String def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isString()) {
            throw new IllegalArgumentException(key + " must be a string, got: " + describe(v));
        }
        return v.asString();
    }

    static int intRange(ILuaTable t, String key, int def, int min, int max) {
        double raw = number(t, key, def);
        if (raw < min || raw > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ", got: " + raw);
        }
        return (int) Math.floor(raw);
    }

    static String resourceId(ILuaTable t, String key, String def) {
        String value = string(t, key, def);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        if (!RESOURCE_LOCATION.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must be a valid resource location (e.g. 'mymod:block'), got: " + value);
        }
        return value;
    }

    @Nullable
    static Double optionalNumber(ILuaTable t, String key) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return null;
        }
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number or nil, got: " + describe(v));
        }
        return v.asDouble();
    }

    static double number(ILuaTable t, String key, double fallback, String where) {
        try {
            return number(t, key, fallback);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + "." + key + " is invalid: " + e.getMessage(), e);
        }
    }

    static int intValue(ILuaTable t, String key, int fallback, String where) {
        return (int) Math.floor(number(t, key, fallback, where));
    }

    static IllegalArgumentException invalid(String where, IllegalArgumentException cause) {
        return new IllegalArgumentException(where + " is invalid: " + cause.getMessage(), cause);
    }

    /** Minecraft resource-location charset: [a-z0-9_.-] for path, optional [a-z0-9_.-] namespace. */
    static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    static boolean isResourceLocation(String id) {
        return id != null && RESOURCE_LOCATION.matcher(id).matches();
    }
}
