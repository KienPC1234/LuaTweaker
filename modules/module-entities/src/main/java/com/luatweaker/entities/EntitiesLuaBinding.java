package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.vm.*;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EntitiesLuaBinding {

    private static final String DYNAMIC_GET_GLOBAL = "__luatweaker_dynamic_get";
    private static final String DYNAMIC_SET_GLOBAL = "__luatweaker_dynamic_set";

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
                case "LookVector", "lookVector" -> {
                    return buildVector3(engine, entity.getLookVectorX(), entity.getLookVectorY(), entity.getLookVectorZ());
                }
                default -> {
                    ILuaValue existing = table.rawget(key);
                    if (existing != null && !existing.isNil()) {
                        return existing;
                    }

                    // Fallback to the engine's dynamic Java bridge for unknown properties
                    // (e.g. modded fields). Lua indexing triggers the proxy __index metamethod,
                    // which translates the property name into a Java getter call.
                    Object rawJavaObj = entity.getRawEntity();
                    if (rawJavaObj != null) {
                        ILuaValue result = readDynamicProperty(engine, rawJavaObj, key);
                        if (result != null) {
                            return result;
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

                    // 2. Fallback to the dynamic Java bridge setter. Lua assignment triggers
                    // the proxy __newindex metamethod, which invokes the Java setter.
                    Object rawJavaObj = entity.getRawEntity();
                    if (rawJavaObj != null && writeDynamicProperty(engine, rawJavaObj, key, val)) {
                        return engine.nilValue();
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

    /**
     * Reads an unknown property through the engine's dynamic Java bridge. Lua's indexing
     * operator dispatches to the proxy {@code __index} metamethod, which resolves the
     * property name to a Java getter in an engine-neutral way.
     *
     * @return the property value, or {@code null} when absent or when the bridge failed;
     *         bridge failures are logged by the engine
     */
    @Nullable
    public static ILuaValue readDynamicProperty(@NotNull ILuaEngine engine,
                                                @NotNull Object rawJavaObj,
                                                @NotNull String key) {
        ILuaValue wrapped;
        ILuaValue getFn;
        try {
            wrapped = engine.wrapDynamicJavaObject(rawJavaObj);
            getFn = dynamicHelper(engine, DYNAMIC_GET_GLOBAL,
                    "function(obj, key) return obj[key] end");
        } catch (RuntimeException e) {
            AsyncFileLogger.get().warn(LogStage.RUNTIME_ERROR,
                    "[EntitiesLuaBinding] Dynamic bridge read setup failed for '" + key + "': " + e.getMessage());
            return null;
        }
        if (wrapped == null || wrapped.isNil() || getFn == null || getFn.isNil()) {
            return null;
        }
        ILuaValue result = engine.callFunction(getFn, wrapped, engine.wrapString(key));
        return (result == null || result.isNil()) ? null : result;
    }

    /**
     * Writes an unknown property through the engine's dynamic Java bridge. Lua's assignment
     * dispatches to the proxy {@code __newindex} metamethod. A {@code pcall} wrapper reports
     * a failed setter instead of silently falling through, so callers can keep the old
     * "set on the Lua table when the bridge rejects the write" behavior.
     *
     * @return {@code true} when the bridge accepted the write without raising an error
     */
    public static boolean writeDynamicProperty(@NotNull ILuaEngine engine,
                                               @NotNull Object rawJavaObj,
                                               @NotNull String key,
                                               @NotNull ILuaValue val) {
        ILuaValue wrapped;
        ILuaValue setFn;
        try {
            wrapped = engine.wrapDynamicJavaObject(rawJavaObj);
            setFn = dynamicHelper(engine, DYNAMIC_SET_GLOBAL,
                    "function(obj, key, value)\n"
                            + "    local ok, err = pcall(function() obj[key] = value end)\n"
                            + "    if ok then return true end\n"
                            + "    return false, tostring(err)\n"
                            + "end");
        } catch (RuntimeException e) {
            AsyncFileLogger.get().warn(LogStage.RUNTIME_ERROR,
                    "[EntitiesLuaBinding] Dynamic bridge write setup failed for '" + key + "': " + e.getMessage());
            return false;
        }
        if (wrapped == null || wrapped.isNil() || setFn == null || setFn.isNil()) {
            return false;
        }
        ILuaValue[] results = engine.callFunctionMulti(setFn, wrapped, engine.wrapString(key), val);
        if (results.length == 0 || !results[0].asBoolean()) {
            String detail = results.length > 1 ? results[1].asString() : "unknown error";
            AsyncFileLogger.get().warn(LogStage.RUNTIME_ERROR,
                    "[EntitiesLuaBinding] Dynamic bridge write failed for '" + key + "': " + detail);
            return false;
        }
        return true;
    }

    /**
     * Lazily installs a small indexing helper in the engine's globals. Wrapping the dynamic
     * bridge this way keeps these bindings engine-neutral: no Cobalt or luava symbols are
     * referenced, and both engines dispatch {@code obj[key]} through the same metamethods.
     */
    private static ILuaValue dynamicHelper(@NotNull ILuaEngine engine,
                                            @NotNull String globalName,
                                            @NotNull String source) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue helper = globals.rawget(globalName);
        if (helper == null || helper.isNil()) {
            engine.executeString(globalName + " = " + source, "luatweaker_dynamic_bridge");
            helper = globals.rawget(globalName);
        }
        return helper;
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

        ILuaFunction getLocalPlayerFn = args -> {
            com.luatweaker.api.entity.IPlayer lp = com.luatweaker.api.pal.Platform.getEntity().getLocalPlayer();
            return lp != null ? createPlayerLuaTable(engine, lp) : engine.nilValue();
        };
        playersTable.rawset("GetLocalPlayer", getLocalPlayerFn);
        playersTable.rawset("getLocalPlayer", getLocalPlayerFn);

        ILuaTable meta = engine.createTable();
        meta.rawset("__index", (ILuaFunction) args -> {
            String key = args[1].asString();
            if ("LocalPlayer".equals(key) || "localPlayer".equals(key)) {
                com.luatweaker.api.entity.IPlayer lp = com.luatweaker.api.pal.Platform.getEntity().getLocalPlayer();
                return lp != null ? createPlayerLuaTable(engine, lp) : engine.nilValue();
            }
            return playersTable.rawget(key);
        });
        playersTable.setMetatable(meta);

        engine.registerService("Players", playersTable);
        engine.registerGlobal("Players", playersTable);
    }
}
