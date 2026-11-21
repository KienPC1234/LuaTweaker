package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.interaction.InteractionLuaBinding;
import com.luatweaker.platform.interaction.NeoForgeInteractableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Wraps block positions as Lua tables (single position and array forms). */
final class PayloadBlocks {

    private static final String DIMENSION = "dimension";

    private PayloadBlocks() {
    }

    static void putBlockAndPos(@NotNull ILuaEngine engine, @NotNull ILuaTable table,
                               @Nullable LevelAccessor level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        String dimension = dimensionOf(level);
        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine,
                new NeoForgeInteractableBlock(dimension, pos.getX(), pos.getY(), pos.getZ()));
        table.rawset("block", blockTable);
        table.rawset("Block", blockTable);
        ILuaTable posTable = buildPosTable(engine, pos);
        table.rawset("pos", posTable);
        table.rawset("Pos", posTable);
        PayloadTables.putBoth(table, DIMENSION, engine.wrapString(dimension));
    }

    static @NotNull ILuaTable buildPosTable(@NotNull ILuaEngine engine, @NotNull BlockPos pos) {
        ILuaTable posTable = engine.createTable();
        posTable.rawset("X", engine.wrapNumber(pos.getX()));
        posTable.rawset("Y", engine.wrapNumber(pos.getY()));
        posTable.rawset("Z", engine.wrapNumber(pos.getZ()));
        posTable.rawset("x", engine.wrapNumber(pos.getX()));
        posTable.rawset("y", engine.wrapNumber(pos.getY()));
        posTable.rawset("z", engine.wrapNumber(pos.getZ()));
        return posTable;
    }

    /** Builds a 1-based Lua array of position tables. */
    static @NotNull ILuaTable buildPosArray(@NotNull ILuaEngine engine, @NotNull List<BlockPos> positions) {
        ILuaTable array = engine.createTable();
        for (int index = 0; index < positions.size(); index++) {
            array.rawset(index + 1, buildPosTable(engine, positions.get(index)));
        }
        return array;
    }

    static @NotNull String dimensionOf(@NotNull LevelAccessor level) {
        return level instanceof Level realLevel
                ? realLevel.dimension().location().toString()
                : "minecraft:overworld";
    }
}
