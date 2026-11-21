package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;

/**
 * Table construction shared by every payload builder: the {@code rawEvent} bridge plus the
 * camelCase/PascalCase field helper so no builder repeats the two-spelling contract.
 */
final class PayloadTables {

    private PayloadTables() {
    }

    static @NotNull ILuaTable base(@NotNull ILuaEngine engine, @NotNull Event event) {
        ILuaTable table = engine.createTable();
        table.rawset("rawEvent", engine.wrapDynamicJavaObject(event));
        return table;
    }

    /** Writes both {@code key} and its PascalCase alias, matching the Lua field contract. */
    static void putBoth(@NotNull ILuaTable table, @NotNull String key, @NotNull ILuaValue value) {
        table.rawset(key, value);
        putPascal(table, key, value);
    }

    /** Writes only the PascalCase alias of {@code key}. */
    static void putPascal(@NotNull ILuaTable table, @NotNull String key, @NotNull ILuaValue value) {
        table.rawset(Character.toUpperCase(key.charAt(0)) + key.substring(1), value);
    }
}
