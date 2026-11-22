package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Builds Lua payload tables for explosion events.
 */
public final class ExplosionPayloads {

    private ExplosionPayloads() {
    }

    public static @NotNull ILuaTable buildStart(@NotNull ILuaEngine engine, @NotNull Level level,
                                                @NotNull Explosion explosion) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "radius", engine.wrapNumber(explosion.radius()));
        var center = explosion.center();
        PayloadTables.putBoth(table, "x", engine.wrapNumber(center.x));
        PayloadTables.putBoth(table, "y", engine.wrapNumber(center.y));
        PayloadTables.putBoth(table, "z", engine.wrapNumber(center.z));
        PayloadTables.putBoth(table, "dimension", engine.wrapString(level.dimension().location().toString()));
        return table;
    }

    public static @NotNull ILuaTable buildDetonate(@NotNull ILuaEngine engine, @NotNull Level level,
                                                   @NotNull Explosion explosion,
                                                   @Nullable List<Entity> affectedEntities) {
        ILuaTable table = buildStart(engine, level, explosion);
        if (affectedEntities != null && !affectedEntities.isEmpty()) {
            ILuaTable entitiesTable = engine.createTable();
            int index = 1;
            for (Entity e : affectedEntities) {
                ILuaTable wrapped = PayloadEntities.wrap(engine, e);
                if (wrapped != null) {
                    entitiesTable.rawset(index++, wrapped);
                }
            }
            PayloadTables.putBoth(table, "affectedEntities", entitiesTable);
        }
        return table;
    }
}
