package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.common.interaction.MinecraftInteractableBlock;
import com.luatweaker.interaction.InteractionLuaBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Wraps block positions and interactable blocks as Lua tables.
 */
public final class PayloadBlocks {

    private static final String DIMENSION = "dimension";

    private PayloadBlocks() {
    }

    public static void putBlockAndPos(@NotNull ILuaEngine engine, @NotNull ILuaTable table,
                                      @Nullable LevelAccessor level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        String dimension = dimensionOf(level);
        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine,
                new MinecraftInteractableBlock(dimension, pos.getX(), pos.getY(), pos.getZ()));
        table.rawset("block", blockTable);
        table.rawset("Block", blockTable);
        ILuaTable posTable = buildPosTable(engine, pos);
        table.rawset("pos", posTable);
        table.rawset("Pos", posTable);
        PayloadTables.putBoth(table, DIMENSION, engine.wrapString(dimension));
    }

    public static @NotNull ILuaTable buildPosTable(@NotNull ILuaEngine engine, @NotNull BlockPos pos) {
        ILuaTable posTable = engine.createTable();
        posTable.rawset("X", engine.wrapNumber(pos.getX()));
        posTable.rawset("Y", engine.wrapNumber(pos.getY()));
        posTable.rawset("Z", engine.wrapNumber(pos.getZ()));
        posTable.rawset("x", engine.wrapNumber(pos.getX()));
        posTable.rawset("y", engine.wrapNumber(pos.getY()));
        posTable.rawset("z", engine.wrapNumber(pos.getZ()));
        return posTable;
    }

    public static @NotNull ILuaTable buildPosArray(@NotNull ILuaEngine engine, @NotNull List<BlockPos> positions) {
        ILuaTable array = engine.createTable();
        int index = 1;
        for (BlockPos pos : positions) {
            array.rawset(index++, buildPosTable(engine, pos));
        }
        return array;
    }

    private static @NotNull String dimensionOf(@NotNull LevelAccessor level) {
        if (level instanceof Level l) {
            return l.dimension().location().toString();
        }
        return "minecraft:overworld";
    }
}
