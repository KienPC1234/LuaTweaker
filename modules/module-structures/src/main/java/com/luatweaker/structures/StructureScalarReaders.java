package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Typed scalar readers for structure config tables: rawget plus a typed
 * default, mirroring the convention used by the dimension parser. Every
 * reader fails fast with an {@link IllegalArgumentException} naming the key
 * instead of dying later at datapack load.
 */
final class StructureScalarReaders {

    /** Minecraft resource-location charset: [a-z0-9_.-] for namespace, path may also contain '/'. */
    private static final Pattern RESOURCE_LOCATION =
            Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    private StructureScalarReaders() {}

    static boolean isResourceLocation(@Nullable String id) {
        return id != null && RESOURCE_LOCATION.matcher(id).matches();
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

    static boolean bool(ILuaTable t, String key, boolean def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean, got: " + describe(v));
        }
        return v.asBoolean();
    }

    static String choice(ILuaTable t, String key, String def, Set<String> allowed) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isString()) {
            throw new IllegalArgumentException(key + " must be a string, got: " + describe(v));
        }
        String value = v.asString();
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(key + " must be one of " + allowed + ", got: " + value);
        }
        return value;
    }

    static int intRange(ILuaTable t, String key, int def, int min, int max) {
        double raw = number(t, key, def);
        if (!Double.isFinite(raw) || raw < min || raw > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ", got: " + raw);
        }
        return (int) Math.floor(raw);
    }

    static String resourceId(ILuaTable t, String key, @Nullable String def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isString()) {
            throw new IllegalArgumentException(key + " must be a string, got: " + describe(v));
        }
        String value = v.asString();
        if (!isResourceLocation(value)) {
            throw new IllegalArgumentException(key + " must be a valid resource location (e.g. 'mymod:id'), got: "
                    + value);
        }
        return value;
    }

    @NotNull
    static String describe(@Nullable ILuaValue v) {
        return v == null ? "nil" : v.getClass().getSimpleName();
    }
}
