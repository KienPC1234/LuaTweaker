package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

/**
 * Shared table construction for event payloads: wraps event objects and enforces
 * the Two-Spelling Contract (camelCase and PascalCase fields).
 */
public final class PayloadTables {

    private PayloadTables() {
    }

    public static @NotNull ILuaTable base(@NotNull ILuaEngine engine, @NotNull Object event) {
        ILuaTable table = engine.createTable();
        table.rawset("rawEvent", engine.wrapDynamicJavaObject(event));
        return table;
    }

    public static void putBoth(@NotNull ILuaTable table, @NotNull String key, @NotNull ILuaValue value) {
        table.rawset(key, value);
        putPascal(table, key, value);
    }

    public static void putBoth(@NotNull ILuaTable table, @NotNull String key, @NotNull ILuaFunction fn) {
        table.rawset(key, fn);
        table.rawset(Character.toUpperCase(key.charAt(0)) + key.substring(1), fn);
    }

    public static void putPascal(@NotNull ILuaTable table, @NotNull String key, @NotNull ILuaValue value) {
        table.rawset(Character.toUpperCase(key.charAt(0)) + key.substring(1), value);
    }
}
