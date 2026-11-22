package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;

/**
 * Builds Lua payload tables for player lifecycle, progression and UI events.
 */
public final class PlayerPayloads {

    private PlayerPayloads() {
    }

    public static @NotNull ILuaTable buildJoin(@NotNull ILuaEngine engine, @NotNull Player player) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        return table;
    }

    public static @NotNull ILuaTable buildLeave(@NotNull ILuaEngine engine, @NotNull Player player) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        return table;
    }

    public static @NotNull ILuaTable buildRespawn(@NotNull ILuaEngine engine, @NotNull Player player,
                                                 boolean conqueredEnd) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadTables.putBoth(table, "conqueredEnd", engine.wrapBoolean(conqueredEnd));
        return table;
    }

    public static @NotNull ILuaTable buildAdvancement(@NotNull ILuaEngine engine, @NotNull Player player,
                                                     @NotNull ResourceLocation advancementId) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadTables.putBoth(table, "advancement", engine.wrapString(advancementId.toString()));
        return table;
    }

    public static @NotNull ILuaTable buildContainer(@NotNull ILuaEngine engine, @NotNull Player player,
                                                   @NotNull AbstractContainerMenu menu, @NotNull String action) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadTables.putBoth(table, "action", engine.wrapString(action));
        PayloadTables.putBoth(table, "containerId", engine.wrapNumber(menu.containerId));
        return table;
    }

    public static @NotNull ILuaTable buildChangeDimension(@NotNull ILuaEngine engine, @NotNull Player player,
                                                          @NotNull ResourceLocation oldDim,
                                                          @NotNull ResourceLocation newDim) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadTables.putBoth(table, "from", engine.wrapString(oldDim.toString()));
        PayloadTables.putBoth(table, "to", engine.wrapString(newDim.toString()));
        return table;
    }
}
