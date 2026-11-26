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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class EntitiesLuaBinding {

    private static final String DYNAMIC_GET_GLOBAL = "__luatweaker_dynamic_get";
    private static final String DYNAMIC_SET_GLOBAL = "__luatweaker_dynamic_set";

    private record PrototypeMetatables(ILuaTable entityMeta, ILuaTable playerMeta) {}

    private static final Map<ILuaEngine, PrototypeMetatables> PROTOTYPE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private EntitiesLuaBinding() {}

    static {
        // Any IEntity/IPlayer returned from a bound method becomes a full entity/player Lua table.
        LuaBinder.registerReturnConverter(IPlayer.class, (engine, value) ->
                value instanceof IPlayer player ? createPlayerLuaTable(engine, player) : engine.nilValue());
        LuaBinder.registerReturnConverter(IEntity.class, (engine, value) ->
                value instanceof IEntity entity ? createEntityLuaTable(engine, entity) : engine.nilValue());
    }

    private static PrototypeMetatables buildPrototypeMetatables(@NotNull ILuaEngine engine) {
        ILuaTable entityProto = LuaBinder.bindPrototype(engine, IEntity.class, EntitiesLuaBinding::getEntityFromTable);
        EntityPositionHelpers.addPrototypePositionHelpers(engine, entityProto);
        ILuaTable entityMeta = EntityPropertyAliases.createSharedMetatable(engine, entityProto, false);

        ILuaTable playerProto = LuaBinder.bindPrototype(engine, IPlayer.class, EntitiesLuaBinding::getEntityFromTable);
        playerProto.rawset("actionText", playerProto.rawget("sendActionBar"));
        EntityPositionHelpers.addPrototypePositionHelpers(engine, playerProto);
        ILuaTable playerMeta = EntityPropertyAliases.createSharedMetatable(engine, playerProto, true);

        return new PrototypeMetatables(entityMeta, playerMeta);
    }

    public static ILuaTable createPlayerLuaTable(ILuaEngine engine, IPlayer player) {
        ILuaTable table = engine.createTable();
        table.rawset("__entity", engine.wrapUserdata(player));
        table.setMetatable(PROTOTYPE_CACHE.computeIfAbsent(engine, EntitiesLuaBinding::buildPrototypeMetatables).playerMeta());
        return table;
    }

    public static ILuaTable createEntityLuaTable(ILuaEngine engine, IEntity entity) {
        ILuaTable table = engine.createTable();
        table.rawset("__entity", engine.wrapUserdata(entity));
        table.setMetatable(PROTOTYPE_CACHE.computeIfAbsent(engine, EntitiesLuaBinding::buildPrototypeMetatables).entityMeta());
        return table;
    }

    public static IEntity getEntityFromTable(@Nullable ILuaValue tableVal) {
        if (tableVal == null || !tableVal.isTable()) {
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
        bindPlayerLookupMethods(engine, playersTable);

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

    private static void bindPlayerLookupMethods(ILuaEngine engine, ILuaTable playersTable) {
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

        ILuaFunction getPlayerFn = args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length <= off || args[off].isNil()) {
                return engine.nilValue();
            }
            String identifier = args[off].asString().trim();
            IPlayer found = identifier.isEmpty() ? null : findPlayer(identifier);
            return found != null ? createPlayerLuaTable(engine, found) : engine.nilValue();
        };
        playersTable.rawset("GetPlayer", getPlayerFn);
        playersTable.rawset("getPlayer", getPlayerFn);
    }

    @Nullable
    private static IPlayer findPlayer(String identifier) {
        IPlayer byUuid = Platform.getEntity().getPlayer(identifier);
        if (byUuid != null) {
            return byUuid;
        }
        IPlayer caseInsensitiveMatch = null;
        for (IPlayer player : Platform.getEntity().getAllPlayers()) {
            if (player.getUuid().equalsIgnoreCase(identifier) || player.getName().equals(identifier)) {
                return player;
            }
            if (caseInsensitiveMatch == null && player.getName().equalsIgnoreCase(identifier)) {
                caseInsensitiveMatch = player;
            }
        }
        return caseInsensitiveMatch;
    }
}
