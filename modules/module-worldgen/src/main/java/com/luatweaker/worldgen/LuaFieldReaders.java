package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed readers for the Lua option/spec tables. Every reader returns the documented
 * fallback when the key is absent and rejects a wrong Lua type loudly; validation of
 * the value itself stays in {@link WorldgenValidation} / {@link WorldgenPlacementParser}.
 */
final class LuaFieldReaders {

    private static final String SPEC_PREFIX = "spec.";

    private LuaFieldReaders() {}

    static @NotNull String reqString(@NotNull ILuaTable table, @NotNull String key,
                                     @NotNull String what) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(what + " is required");
        }
        String result = value.asString();
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        return result;
    }

    static @Nullable String optString(@NotNull ILuaTable table, @NotNull String key) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return null;
        }
        return value.asString();
    }

    static int optInt(@NotNull ILuaTable table, @NotNull String key, int fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(SPEC_PREFIX + key + " must be a number");
        }
        return (int) value.asDouble();
    }

    static double optDouble(@NotNull ILuaTable table, @NotNull String key, double fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(SPEC_PREFIX + key + " must be a number");
        }
        return value.asDouble();
    }

    static boolean optBoolean(@NotNull ILuaTable table, @NotNull String key, boolean fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(SPEC_PREFIX + key + " must be a boolean");
        }
        return value.asBoolean();
    }

    static boolean hasExplicit(@NotNull ILuaTable table, @NotNull String key) {
        ILuaValue value = table.rawget(key);
        return value != null && !value.isNil();
    }

    static @NotNull String optAnchor(@NotNull ILuaTable table, @NotNull String key) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return WorldgenDefaults.DEFAULT_ANCHOR;
        }
        String anchor = value.asString();
        if (!WorldgenDefaults.KNOWN_ANCHORS.contains(anchor)) {
            throw new IllegalArgumentException(SPEC_PREFIX + key + " must be one of "
                    + WorldgenDefaults.KNOWN_ANCHORS + ", got: " + anchor);
        }
        return anchor;
    }

    static @NotNull String optStep(@NotNull ILuaTable table, @NotNull String key,
                                   @NotNull String fallback) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return fallback;
        }
        return WorldgenValidation.parseStep(value.asString(), "options." + key);
    }
}
