package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/**
 * Position helpers for entity Lua tables ({@code getPos}/{@code setPos} plus
 * PascalCase aliases). Teleport argument parsing lives here so the table
 * factory stays thin.
 */
final class EntityPositionHelpers {

    /** A setPos call needs either 3 numbers or a single position table. */
    private static final int POS_COMPONENT_COUNT = 3;

    private EntityPositionHelpers() {}

    static void addPositionHelpers(ILuaEngine engine, ILuaTable table, IEntity entity) {
        table.rawset("getPos", args -> {
            ILuaTable pos = engine.createTable();
            pos.rawset("x", engine.wrapNumber(entity.getX()));
            pos.rawset("y", engine.wrapNumber(entity.getY()));
            pos.rawset("z", engine.wrapNumber(entity.getZ()));
            pos.rawset("X", pos.rawget("x"));
            pos.rawset("Y", pos.rawget("y"));
            pos.rawset("Z", pos.rawget("z"));
            return pos;
        });
        table.rawset("GetPos", table.rawget("getPos"));

        table.rawset("setPos", args -> {
            teleportFromArgs(entity, args, LuaBinder.getOffset(args));
            return engine.nilValue();
        });
        table.rawset("SetPos", table.rawget("setPos"));
    }

    private static void teleportFromArgs(IEntity entity, ILuaValue[] args, int off) {
        if (args.length - off >= POS_COMPONENT_COUNT) {
            entity.teleport(args[off].asDouble(), args[off + 1].asDouble(), args[off + 2].asDouble());
            return;
        }
        if (args.length - off >= 1 && args[off].isTable()) {
            teleportFromTable(entity, args[off].asTable());
        }
    }

    private static void teleportFromTable(IEntity entity, ILuaTable pos) {
        entity.teleport(
                coordOrDefault(pos, "X", "x", entity.getX()),
                coordOrDefault(pos, "Y", "y", entity.getY()),
                coordOrDefault(pos, "Z", "z", entity.getZ()));
    }

    private static double coordOrDefault(ILuaTable pos, String upperKey, String lowerKey, double fallback) {
        ILuaValue val = pos.rawget(upperKey);
        if (val == null || val.isNil()) {
            val = pos.rawget(lowerKey);
        }
        return val != null && !val.isNil() ? val.asDouble() : fallback;
    }
}
