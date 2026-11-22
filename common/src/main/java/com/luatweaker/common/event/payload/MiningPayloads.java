package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds Lua payload tables for mining, building and interaction events.
 */
public final class MiningPayloads {

    private MiningPayloads() {
    }

    public static @NotNull ILuaTable buildBreak(@NotNull ILuaEngine engine, @NotNull Level level,
                                               @NotNull BlockPos pos, @NotNull BlockState state,
                                               @Nullable Player player, int exp) {
        ILuaTable table = engine.createTable();
        if (player != null) {
            PayloadEntities.putPlayer(engine, table, player);
        }
        PayloadBlocks.putBlockAndPos(engine, table, level, pos);
        PayloadTables.putBoth(table, "exp", engine.wrapNumber(exp));
        return table;
    }

    public static @NotNull ILuaTable buildPlace(@NotNull ILuaEngine engine, @NotNull Level level,
                                               @NotNull BlockPos pos, @NotNull BlockState state,
                                               @Nullable Entity entity) {
        ILuaTable table = engine.createTable();
        if (entity instanceof Player player) {
            PayloadEntities.putPlayer(engine, table, player);
        } else if (entity != null) {
            PayloadEntities.putEntity(engine, table, "entity", "Entity", entity);
        }
        PayloadBlocks.putBlockAndPos(engine, table, level, pos);
        return table;
    }

    public static @NotNull ILuaTable buildInteractBlock(@NotNull ILuaEngine engine, @NotNull Player player,
                                                       @NotNull InteractionHand hand, @NotNull Level level,
                                                       @NotNull BlockPos pos, @Nullable Direction direction) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadBlocks.putBlockAndPos(engine, table, level, pos);
        PayloadTables.putBoth(table, "direction",
                engine.wrapString(direction != null ? direction.name() : "UP"));
        PayloadTables.putBoth(table, "hand", engine.wrapString(hand.name()));
        return table;
    }

    public static @NotNull ILuaTable buildInteractItem(@NotNull ILuaEngine engine, @NotNull Player player,
                                                      @NotNull InteractionHand hand, @NotNull ItemStack stack) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadEntities.putItem(engine, table, stack);
        PayloadTables.putBoth(table, "hand", engine.wrapString(hand.name()));
        return table;
    }

    public static @NotNull ILuaTable buildBreakSpeed(@NotNull ILuaEngine engine, @NotNull Player player,
                                                    @NotNull BlockState state, @NotNull BlockPos pos,
                                                    float originalSpeed, float newSpeed) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadBlocks.putBlockAndPos(engine, table, player.level(), pos);
        PayloadTables.putBoth(table, "originalSpeed", engine.wrapNumber(originalSpeed));
        PayloadTables.putBoth(table, "newSpeed", engine.wrapNumber(newSpeed));
        return table;
    }
}
