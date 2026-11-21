package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Explosion payloads. {@code ExplosionDetonate} exposes the live affected-block list so a Lua
 * script can veto individual blocks (claim protection) before vanilla destroys them.
 */
final class ExplosionPayloads {

    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String DIMENSION = "dimension";
    private static final String AFFECTED_BLOCKS = "affectedBlocks";
    private static final String AFFECTED_ENTITIES = "affectedEntities";
    private static final String REMOVE_BLOCK = "removeBlock";
    private static final String CLEAR_BLOCKS = "clearBlocks";
    private static final int MIN_ONE_ARG = 1;
    private static final int MIN_THREE_ARGS = 3;

    private ExplosionPayloads() {
    }

    static void register(@NotNull PayloadRegistry registry) {
        registry.add(ExplosionEvent.Start.class, ExplosionPayloads::buildStart);
        registry.add(ExplosionEvent.Detonate.class, ExplosionPayloads::buildDetonate);
    }

    private static ILuaTable buildStart(ILuaEngine engine, ExplosionEvent.Start event) {
        ILuaTable table = PayloadTables.base(engine, event);
        putCoords(engine, table, event.getExplosion().center().x,
                event.getExplosion().center().y, event.getExplosion().center().z);
        PayloadTables.putBoth(table, "radius", engine.wrapNumber(event.getExplosion().radius()));
        if (event.getExplosion().getDirectSourceEntity() != null) {
            PayloadEntities.putEntity(engine, table, "exploder", "Exploder",
                    event.getExplosion().getDirectSourceEntity());
        }
        PayloadTables.putBoth(table, DIMENSION,
                engine.wrapString(PayloadBlocks.dimensionOf(event.getLevel())));
        return table;
    }

    private static ILuaTable buildDetonate(ILuaEngine engine, ExplosionEvent.Detonate event) {
        ILuaTable table = PayloadTables.base(engine, event);
        putCoords(engine, table, event.getExplosion().center().x,
                event.getExplosion().center().y, event.getExplosion().center().z);
        PayloadTables.putBoth(table, DIMENSION,
                engine.wrapString(PayloadBlocks.dimensionOf(event.getLevel())));
        putAffected(engine, table, event.getAffectedBlocks(), event.getAffectedEntities());
        putBlockVeto(engine, table, event.getAffectedBlocks());
        return table;
    }

    private static void putAffected(ILuaEngine engine, ILuaTable table, List<BlockPos> blocks,
                                    List<Entity> entities) {
        PayloadTables.putBoth(table, AFFECTED_BLOCKS, PayloadBlocks.buildPosArray(engine, blocks));
        ILuaTable entityArray = engine.createTable();
        int slot = 1;
        for (Entity entity : entities) {
            ILuaTable wrapped = PayloadEntities.wrap(engine, entity);
            if (wrapped != null) {
                entityArray.rawset(slot++, wrapped);
            }
        }
        PayloadTables.putBoth(table, AFFECTED_ENTITIES, entityArray);
    }

    /** Mutating the live list before vanilla finalizes the explosion actually saves the blocks. */
    private static void putBlockVeto(ILuaEngine engine, ILuaTable table, List<BlockPos> blocks) {
        table.rawset(REMOVE_BLOCK, args -> {
            BlockPos pos = readPos(args, LuaBinder.getOffset(args));
            return engine.wrapBoolean(pos != null && blocks.removeIf(pos::equals));
        });
        PayloadTables.putPascal(table, REMOVE_BLOCK, table.rawget(REMOVE_BLOCK));
        table.rawset(CLEAR_BLOCKS, args -> {
            blocks.clear();
            return engine.nilValue();
        });
        PayloadTables.putPascal(table, CLEAR_BLOCKS, table.rawget(CLEAR_BLOCKS));
    }

    private static @Nullable BlockPos readPos(ILuaValue[] args, int off) {
        if (args.length - off >= MIN_THREE_ARGS) {
            return BlockPos.containing(args[off].asDouble(), args[off + 1].asDouble(),
                    args[off + 2].asDouble());
        }
        if (args.length - off >= MIN_ONE_ARG && args[off].isTable()) {
            return readPosTable(args[off].asTable());
        }
        return null;
    }

    private static @Nullable BlockPos readPosTable(ILuaTable posTable) {
        ILuaValue x = posTable.rawget("X");
        ILuaValue y = posTable.rawget("Y");
        ILuaValue z = posTable.rawget("Z");
        if (x == null || y == null || z == null) {
            return null;
        }
        return BlockPos.containing(x.asDouble(), y.asDouble(), z.asDouble());
    }

    private static void putCoords(ILuaEngine engine, ILuaTable table, double x, double y, double z) {
        PayloadTables.putBoth(table, X, engine.wrapNumber(x));
        PayloadTables.putBoth(table, Y, engine.wrapNumber(y));
        PayloadTables.putBoth(table, Z, engine.wrapNumber(z));
    }
}
