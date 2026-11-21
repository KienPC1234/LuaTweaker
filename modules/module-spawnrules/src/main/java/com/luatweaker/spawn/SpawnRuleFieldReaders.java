package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Scalar and range readers for spawn-rule tables: rawget plus a typed default.
 * Every error names the rule and the offending field, so a broken script fails
 * at registration time instead of silently spawning nothing.
 */
final class SpawnRuleFieldReaders {

    private SpawnRuleFieldReaders() {}

    @NotNull
    static String requiredString(@NotNull ILuaTable table, @NotNull String key,
                                 @NotNull String where) {
        String value = optionalString(table, key, where);
        if (value == null) {
            throw new IllegalArgumentException(where + "." + key + " is required");
        }
        return value;
    }

    @Nullable
    static String optionalString(@NotNull ILuaTable table, @NotNull String key,
                                 @NotNull String where) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(where + "." + key + " must be a string, got: "
                    + value.getClass().getSimpleName());
        }
        return value.asString();
    }

    static int intField(@NotNull ILuaTable table, @NotNull String key, int fallback,
                        int min, int max, @NotNull String where) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(where + "." + key + " must be a number, got: "
                    + value.getClass().getSimpleName());
        }
        int parsed = (int) Math.floor(value.asDouble());
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(where + "." + key + " must be between "
                    + min + " and " + max + ", got: " + parsed);
        }
        return parsed;
    }

    static boolean boolField(@NotNull ILuaTable table, @NotNull String key, boolean fallback,
                             @NotNull String where) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(where + "." + key + " must be a boolean, got: "
                    + value.getClass().getSimpleName());
        }
        return value.asBoolean();
    }

    /** Parses {@code key = {min, max}}; a missing key keeps {@code [defMin, defMax]}. */
    @NotNull
    static int[] range(@NotNull ILuaTable table, @NotNull String key,
                       int defMin, int defMax, @NotNull String where) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return new int[]{defMin, defMax};
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException(where + "." + key + " must be a table {min, max}");
        }
        int[] bounds = readRangeBounds(value.asTable(), key, where);
        checkRangeBounds(key, defMin, defMax, where, bounds[0], bounds[1]);
        return bounds;
    }

    private static void checkRangeBounds(@NotNull String key, int defMin, int defMax,
                                         @NotNull String where, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(where + "." + key + " min (" + min
                    + ") must be <= max (" + max + ")");
        }
        if (min < defMin || max > defMax) {
            throw new IllegalArgumentException(where + "." + key + " must stay between "
                    + defMin + " and " + defMax + ", got: [" + min + ", " + max + "]");
        }
    }

    @NotNull
    private static int[] readRangeBounds(@NotNull ILuaTable range, @NotNull String key,
                                         @NotNull String where) {
        ILuaValue minValue = range.rawget(1);
        ILuaValue maxValue = range.rawget(2);
        if (minValue == null || !minValue.isNumber() || maxValue == null || !maxValue.isNumber()) {
            throw new IllegalArgumentException(where + "." + key + " must be {min, max} numbers");
        }
        return new int[]{(int) Math.floor(minValue.asDouble()), (int) Math.floor(maxValue.asDouble())};
    }

    @Nullable
    static ILuaValue functionField(@NotNull ILuaTable table, @NotNull String key,
                                   @NotNull String where) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return null;
        }
        if (!value.isFunction()) {
            throw new IllegalArgumentException(where + "." + key + " must be a Lua function");
        }
        return value;
    }
}
