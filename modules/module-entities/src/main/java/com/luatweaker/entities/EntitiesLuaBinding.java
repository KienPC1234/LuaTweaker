package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

import java.util.List;

public final class EntitiesLuaBinding {

    private static final String DYNAMIC_GET_GLOBAL = "__luatweaker_dynamic_get";
    private static final String DYNAMIC_SET_GLOBAL = "__luatweaker_dynamic_set";

    private EntitiesLuaBinding() {}

    static {
        // Any IEntity returned from a bound method becomes a full entity Lua table.
        LuaBinder.registerReturnConverter(IEntity.class, (engine, value) ->
                value instanceof IEntity entity ? createEntityLuaTable(engine, entity) : engine.nilValue());
    }

    public static ILuaTable createPlayerLuaTable(ILuaEngine engine, IPlayer player) {
        ILuaTable table = LuaBinder.bindTable(engine, player, IPlayer.class);
        table.rawset("__entity", engine.wrapUserdata(player));
        table.rawset("actionText", table.rawget("sendActionBar"));
        EntityPositionHelpers.addPositionHelpers(engine, table, player);
        EntityPropertyAliases.addPlayerPropertyAliases(engine, table, player);
        return table;
    }

    public static ILuaTable createEntityLuaTable(ILuaEngine engine, IEntity entity) {
        ILuaTable table = LuaBinder.bindTable(engine, entity, IEntity.class);
        table.rawset("__entity", engine.wrapUserdata(entity));
        EntityPositionHelpers.addPositionHelpers(engine, table, entity);
        EntityPropertyAliases.addPropertyAliases(engine, table, entity);
        return table;
    }

    public static IEntity getEntityFromTable(ILuaValue tableVal) {
        if (!tableVal.isTable()) {
            return null;
        }
        ILuaValue entityVal = tableVal.asTable().rawget("__entity");
        if (entityVal == null || entityVal.isNil()) {
            return null;
        }
        Object obj = entityVal.toJavaObject();
        return obj instanceof IEntity entity ? entity : null;
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
        } catch (LuaException e) {
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
        } catch (LuaException e) {
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
            List<IPlayer> players = Platform.getEntity().getAllPlayers();
            ILuaTable result = engine.createTable();
            for (int i = 0; i < players.size(); i++) {
                result.rawset(i + 1, createPlayerLuaTable(engine, players.get(i)));
            }
            return result;
        };
        playersTable.rawset("GetPlayers", getPlayersFn);
        playersTable.rawset("getPlayers", getPlayersFn);

        ILuaFunction getLocalPlayerFn = args -> {
            IPlayer localPlayer = Platform.getEntity().getLocalPlayer();
            return localPlayer != null ? createPlayerLuaTable(engine, localPlayer) : engine.nilValue();
        };
        playersTable.rawset("GetLocalPlayer", getLocalPlayerFn);
        playersTable.rawset("getLocalPlayer", getLocalPlayerFn);

        ILuaTable meta = engine.createTable();
        meta.rawset("__index", (ILuaFunction) args -> {
            String key = args[1].asString();
            if ("LocalPlayer".equals(key) || "localPlayer".equals(key)) {
                IPlayer localPlayer = Platform.getEntity().getLocalPlayer();
                return localPlayer != null ? createPlayerLuaTable(engine, localPlayer) : engine.nilValue();
            }
            return playersTable.rawget(key);
        });
        playersTable.setMetatable(meta);

        engine.registerService("Players", playersTable);
        engine.registerGlobal("Players", playersTable);
    }
}
