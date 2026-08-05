package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.*;
import com.luatweaker.core.bind.LuaBinder;

public class EntitiesLuaBinding {

    static {
        // Any IEntity returned from a bound method becomes a full entity Lua table.
        LuaBinder.registerReturnConverter(IEntity.class, (engine, value) ->
                value instanceof IEntity entity ? createEntityLuaTable(engine, entity) : engine.nilValue());
    }

    public static ILuaTable createPlayerLuaTable(ILuaEngine engine, IPlayer player) {
        ILuaTable table = LuaBinder.bindTable(engine, player, IPlayer.class);
        table.rawset("__entity", engine.wrapUserdata(player));
        table.rawset("actionText", table.rawget("sendActionBar"));
        addPositionHelpers(engine, table, player);
        addPropertyAliases(engine, table, player);
        return table;
    }

    public static ILuaTable createEntityLuaTable(ILuaEngine engine, IEntity entity) {
        ILuaTable table = LuaBinder.bindTable(engine, entity, IEntity.class);
        table.rawset("__entity", engine.wrapUserdata(entity));
        addPositionHelpers(engine, table, entity);
        addPropertyAliases(engine, table, entity);
        return table;
    }

    private static void addPositionHelpers(ILuaEngine engine, ILuaTable table, IEntity entity) {
        table.rawset("getPos", args -> {
            ILuaTable pos = engine.createTable();
            pos.rawset("x", engine.wrapNumber(entity.getX()));
            pos.rawset("y", engine.wrapNumber(entity.getY()));
            pos.rawset("z", engine.wrapNumber(entity.getZ()));
            return pos;
        });
        table.rawset("setPos", args -> {
            if (args.length >= 4) {
                entity.teleport(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            } else if (args.length >= 2 && args[1].isTable()) {
                ILuaTable pos = args[1].asTable();
                entity.teleport(
                        pos.rawget("X").asDouble(),
                        pos.rawget("Y").asDouble(),
                        pos.rawget("Z").asDouble()
                );
            }
            return engine.nilValue();
        });
    }

    /**
     * Adds Roblox-style property access (entity.Health, entity.MaxHealth, entity.Type,
     * entity.CustomName, entity.Velocity, entity.Position, entity.IsAlive) on top of the
     * generated method-style API, so both syntaxes work on the same table.
     */
    private static void addPropertyAliases(ILuaEngine engine, ILuaTable table, IEntity entity) {
        ILuaTable meta = engine.createTable();

        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key) {
                case "Type", "type" -> { return engine.callFunction(table.rawget("getType"), table); }
                case "Name", "name" -> { return engine.callFunction(table.rawget("getName"), table); }
                case "Health", "health" -> { return engine.callFunction(table.rawget("getHealth"), table); }
                case "MaxHealth", "maxHealth" -> { return engine.callFunction(table.rawget("getMaxHealth"), table); }
                case "CustomName", "customName" -> { return engine.callFunction(table.rawget("getCustomName"), table); }
                case "Position", "position" -> { return buildVector3(engine, entity.getX(), entity.getY(), entity.getZ()); }
                case "Velocity", "velocity" -> {
                    return buildVector3(engine, entity.getMotionX(), entity.getMotionY(), entity.getMotionZ());
                }
                default -> {
                    ILuaValue existing = table.rawget(key);
                    if (existing != null && !existing.isNil()) {
                        return existing;
                    }
                    
                    // Fallback to DynamicJavaProxy for unknown properties (e.g. Modded fields)
                    Object rawJavaObj = entity.getRawEntity();
                    if (rawJavaObj != null && engine instanceof com.luatweaker.core.vm.CobaltLuaEngine cobaltEngine) {
                        org.squiddev.cobalt.LuaUserdata proxy = com.luatweaker.core.bind.DynamicJavaProxy.create(
                                cobaltEngine.getCobaltState(), rawJavaObj);
                        try {
                            org.squiddev.cobalt.LuaValue result = org.squiddev.cobalt.OperationHelper.getTable(
                                    cobaltEngine.getCobaltState(), proxy, org.squiddev.cobalt.ValueFactory.valueOf(key));
                            if (!result.isNil()) {
                                return new com.luatweaker.core.vm.CobaltLuaValue(result);
                            }
                        } catch (org.squiddev.cobalt.LuaError | org.squiddev.cobalt.UnwindThrowable e) {
                            // Ignore and fall through to nil
                        }
                    }
                    
                    return engine.nilValue();
                }
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            ILuaValue val = args[2];
            switch (key) {
                case "Health", "health" -> engine.callFunction(table.rawget("setHealth"), table, val);
                case "MaxHealth", "maxHealth" -> engine.callFunction(table.rawget("setMaxHealth"), table, val);
                case "CustomName", "customName" -> engine.callFunction(table.rawget("setCustomName"), table, val);
                case "Velocity", "velocity" -> {
                    if (val.isTable()) {
                        ILuaTable v = val.asTable();
                        engine.callFunction(table.rawget("setMotion"), table,
                                v.rawget("X"), v.rawget("Y"), v.rawget("Z"));
                    }
                }
                case "Position", "position" -> {
                    if (val.isTable()) {
                        ILuaTable p = val.asTable();
                        engine.callFunction(table.rawget("teleport"), table,
                                p.rawget("X"), p.rawget("Y"), p.rawget("Z"));
                    }
                }
                default -> {
                    // 1. Check if the table itself has it
                    if (!table.rawget(key).isNil()) {
                        table.rawset(key, val);
                        return engine.nilValue();
                    }
                    
                    // 2. Fallback to DynamicJavaProxy setter
                    Object rawJavaObj = entity.getRawEntity();
                    if (rawJavaObj != null && engine instanceof com.luatweaker.core.vm.CobaltLuaEngine cobaltEngine) {
                        org.squiddev.cobalt.LuaUserdata proxy = com.luatweaker.core.bind.DynamicJavaProxy.create(
                                cobaltEngine.getCobaltState(), rawJavaObj);
                        try {
                            org.squiddev.cobalt.LuaValue cobaltVal = org.squiddev.cobalt.Constants.NIL;
                            if (val instanceof com.luatweaker.core.vm.CobaltLuaValue cobaltWrapper) {
                                cobaltVal = cobaltWrapper.getCobaltValue();
                            }
                            org.squiddev.cobalt.OperationHelper.setTable(
                                    cobaltEngine.getCobaltState(), proxy, org.squiddev.cobalt.ValueFactory.valueOf(key), cobaltVal);
                            return engine.nilValue();
                        } catch (org.squiddev.cobalt.LuaError | org.squiddev.cobalt.UnwindThrowable e) {
                            // Ignore and fall through
                        }
                    }
                    
                    // 3. Just set it on the table
                    table.rawset(key, val);
                }
            }
            return engine.nilValue();
        });

        meta.rawset("__eq", args -> {
            IEntity e1 = getEntityFromTable(args[1]);
            IEntity e2 = getEntityFromTable(args[2]);
            if (e1 != null && e2 != null) {
                return engine.wrapBoolean(e1.getUuid().equals(e2.getUuid()));
            }
            return engine.wrapBoolean(false);
        });

        table.setMetatable(meta);
    }

    private static ILuaValue buildVector3(ILuaEngine engine, double x, double y, double z) {
        ILuaValue v3Class = engine.getGlobalEnvironment().rawget("Vector3");
        if (v3Class != null && !v3Class.isNil()) {
            ILuaValue v3New = v3Class.asTable().rawget("new");
            if (v3New != null && !v3New.isNil()) {
                return engine.callFunction(v3New, v3Class,
                        engine.wrapNumber(x), engine.wrapNumber(y), engine.wrapNumber(z));
            }
        }
        ILuaTable fallback = engine.createTable();
        fallback.rawset("X", engine.wrapNumber(x));
        fallback.rawset("Y", engine.wrapNumber(y));
        fallback.rawset("Z", engine.wrapNumber(z));
        return fallback;
    }

    public static com.luatweaker.api.entity.IEntity getEntityFromTable(com.luatweaker.api.vm.ILuaValue tableVal) {
        if (tableVal.isTable()) {
            com.luatweaker.api.vm.ILuaValue entityVal = tableVal.asTable().rawget("__entity");
            if (entityVal != null && !entityVal.isNil()) {
                Object obj = entityVal.toJavaObject();
                if (obj instanceof com.luatweaker.api.entity.IEntity entity) {
                    return entity;
                }
            }
        }
        return null;
    }

    public static void registerBindings(ILuaEngine engine) {
        ILuaTable playersTable = engine.createTable();
        ILuaFunction getPlayersFn = args -> {
            java.util.List<com.luatweaker.api.entity.IPlayer> players = com.luatweaker.api.pal.Platform.getEntity().getAllPlayers();
            ILuaTable result = engine.createTable();
            for (int i = 0; i < players.size(); i++) {
                result.rawset(i + 1, createPlayerLuaTable(engine, players.get(i)));
            }
            return result;
        };
        playersTable.rawset("GetPlayers", getPlayersFn);
        playersTable.rawset("getPlayers", getPlayersFn);
        engine.registerService("Players", playersTable);
        engine.registerGlobal("Players", playersTable);
    }
}
