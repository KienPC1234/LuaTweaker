package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Readers for the "string or list of strings" Lua values used by biome/step lists.
 */
final class LuaStringListReader {

    private LuaStringListReader() {}

    static @NotNull List<String> reqStringList(@NotNull ILuaTable table,
                                               @NotNull String primary,
                                               @Nullable String alias,
                                               @NotNull String what) {
        ILuaValue value = table.rawget(primary);
        if ((value == null || value.isNil()) && alias != null) {
            value = table.rawget(alias);
        }
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(what + " is required (string or list of strings)");
        }
        List<String> result = asStringList(value, what);
        if (result.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        return result;
    }

    static @NotNull List<String> asStringList(@NotNull ILuaValue value, @NotNull String what) {
        if (!value.isTable()) {
            return List.of(singleString(value, what));
        }
        ILuaTable table = value.asTable();
        if (table == null) {
            throw new IllegalArgumentException(what + " must be a string or list of strings");
        }
        return readEntries(table, what);
    }

    private static @NotNull String singleString(@NotNull ILuaValue value, @NotNull String what) {
        String single = value.asString();
        if (single == null || single.isEmpty()) {
            throw new IllegalArgumentException(what + " must be a string or list of strings");
        }
        return single;
    }

    private static @NotNull List<String> readEntries(@NotNull ILuaTable table, @NotNull String what) {
        List<String> result = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue item = table.rawget(i);
            if (item == null || item.isNil()) {
                break;
            }
            String entry = item.asString();
            if (entry == null || entry.isEmpty()) {
                throw new IllegalArgumentException(what + "[" + i + "] must be a string");
            }
            result.add(entry);
        }
        return result;
    }
}
