package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/**
 * Position helpers bound once onto the shared entity/player prototype table
 * ({@code getPos}/{@code setPos} plus PascalCase aliases).
 */
final class EntityPositionHelpers {

    /** A setPos call needs either 3 numbers or a single position table. */
    private static final int POS_COMPONENT_COUNT = 3;

    private EntityPositionHelpers() {}

    static void addPrototypePositionHelpers(ILuaEngine engine, ILuaTable prototype) {
        ILuaFunction getPosFn = args -> {
            IEntity entity = args.length > 0 ? EntitiesLuaBinding.getEntityFromTable(args[0]) : null;
            if (entity == null) {
                return engine.nilValue();
            }
            ILuaTable pos = engine.createTable();
            ILuaValue vx = engine.wrapNumber(entity.getX());
            ILuaValue vy = engine.wrapNumber(entity.getY());
            ILuaValue vz = engine.wrapNumber(entity.getZ());
            pos.rawset("x", vx);
            pos.rawset("y", vy);
            pos.rawset("z", vz);
            pos.rawset("X", vx);
            pos.rawset("Y", vy);
            pos.rawset("Z", vz);
            return pos;
        };
        prototype.rawset("getPos", getPosFn);
        prototype.rawset("GetPos", getPosFn);

        ILuaFunction setPosFn = args -> {
            IEntity entity = args.length > 0 ? EntitiesLuaBinding.getEntityFromTable(args[0]) : null;
            if (entity != null) {
                teleportFromArgs(entity, args, LuaBinder.getOffset(args));
            }
            return engine.nilValue();
        };
        prototype.rawset("setPos", setPosFn);
        prototype.rawset("SetPos", setPosFn);
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
