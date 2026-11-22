package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Builds Lua payload tables for world level lifecycle events.
 */
public final class LevelPayloads {

    private LevelPayloads() {
    }

    public static @NotNull ILuaTable buildLoad(@NotNull ILuaEngine engine, @NotNull Level level) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "dimension", engine.wrapString(level.dimension().location().toString()));
        return table;
    }

    public static @NotNull ILuaTable buildUnload(@NotNull ILuaEngine engine, @NotNull Level level) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "dimension", engine.wrapString(level.dimension().location().toString()));
        return table;
    }
}
