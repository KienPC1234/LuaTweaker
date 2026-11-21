package com.luatweaker.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the RemoteEvent/RemoteFunction registries: lookup, creation from the
 * bootstrapped Lua classes, and the per-remote Lua service tables.
 */
final class RemoteRegistry {

    private static final int MIN_TWO_ARGS = 2;
    private static final int MIN_THREE_ARGS = 3;

    private final ILuaEngine engine;
    private final IRocketNetworkService sender;
    private final Map<String, ILuaTable> remoteEvents;
    private final Map<String, ILuaTable> remoteFunctions;

    RemoteRegistry(ILuaEngine engine, IRocketNetworkService sender,
                   Map<String, ILuaTable> remoteEvents, Map<String, ILuaTable> remoteFunctions) {
        this.engine = engine;
        this.sender = sender;
        this.remoteEvents = remoteEvents;
        this.remoteFunctions = remoteFunctions;
    }

    ILuaTable getOrCreateRemoteEvent(String channelName) {
        requireScriptChannelName(channelName, "RemoteEvent");
        return remoteEvents.computeIfAbsent(channelName, name -> {
            ILuaValue remoteEventClass = engine.getGlobalEnvironment().rawget("RemoteEvent");
            if (remoteEventClass == null || remoteEventClass.isNil()) {
                throw new IllegalStateException("RemoteEvent Lua class not initialized. Did bootstrap run?");
            }
            ILuaValue newFn = remoteEventClass.asTable().rawget("new");
            ILuaValue res = engine.callFunction(newFn, remoteEventClass, engine.wrapString(name),
                    createServiceTable());
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network Registry] [NetService@" + System.identityHashCode(sender)
                            + " / Engine@" + System.identityHashCode(engine) + "] Registered RemoteEvent '"
                            + channelName + "'. All channels in this instance: " + remoteEvents.keySet());
            return res.asTable();
        });
    }

    ILuaTable getOrCreateRemoteFunction(String functionName) {
        requireScriptChannelName(functionName, "RemoteFunction");
        return remoteFunctions.computeIfAbsent(functionName, name -> {
            ILuaValue remoteFnClass = engine.getGlobalEnvironment().rawget("RemoteFunction");
            if (remoteFnClass == null || remoteFnClass.isNil()) {
                throw new IllegalStateException("RemoteFunction Lua class not initialized. Did bootstrap run?");
            }
            ILuaValue newFn = remoteFnClass.asTable().rawget("new");
            ILuaValue res = engine.callFunction(newFn, remoteFnClass, engine.wrapString(name),
                    createServiceTable());
            return res.asTable();
        });
    }

    /** Java-side diagnostic only: names of all registered remote event channels. */
    @NotNull
    Set<String> getRegisteredRemoteEventNames() {
        return Set.copyOf(remoteEvents.keySet());
    }

    private ILuaTable createServiceTable() {
        ILuaTable table = engine.createTable();
        table.rawset("FireServer", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_TWO_ARGS) {
                String name = args[off].asString();
                sender.FireServer(name, fireArgs(args, off, 1));
            }
            return engine.nilValue();
        });
        table.rawset("FireClient", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_THREE_ARGS) {
                String name = args[off].asString();
                String uuid = args[off + 1].asString();
                sender.FireClient(name, uuid, fireArgs(args, off, 2));
            }
            return engine.nilValue();
        });
        table.rawset("FireAllClients", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_TWO_ARGS) {
                String name = args[off].asString();
                sender.FireAllClients(name, fireArgs(args, off, 1));
            }
            return engine.nilValue();
        });
        return table;
    }

    private static ILuaValue[] fireArgs(ILuaValue[] args, int off, int firstValueIndex) {
        return args[off + firstValueIndex].isTable()
                ? unpackTable(args[off + firstValueIndex].asTable())
                : new ILuaValue[0];
    }

    /**
     * Unpacks a Lua vararg-style table into a positional array, preserving order.
     * (asMap() is a HashMap whose iteration order scrambles positional args.)
     */
    static ILuaValue[] unpackTable(ILuaTable tbl) {
        List<ILuaValue> list = new ArrayList<>();
        for (int i = 1; i <= tbl.length(); i++) {
            ILuaValue val = tbl.rawget(i);
            if (val == null || val.isNil()) {
                break;
            }
            list.add(val);
        }
        return list.toArray(new ILuaValue[0]);
    }

    /**
     * Engine channels ({@code LuaTweaker:*}) are routed before RemoteEvents, so a script
     * listener registered under one would either shadow a control command or be silently
     * unreachable. Reject it loudly.
     */
    static void requireScriptChannelName(@Nullable String name, @NotNull String kind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(kind + " name must not be empty");
        }
        if (name.startsWith(NetworkServiceImpl.RESERVED_CHANNEL_PREFIX)) {
            throw new IllegalArgumentException(kind + " name '" + name + "' uses the reserved '"
                    + NetworkServiceImpl.RESERVED_CHANNEL_PREFIX + "' prefix");
        }
    }

    static @Nullable ILuaValue resolvePlayerTable(ILuaEngine engine, String playerUuid) {
        if (!com.luatweaker.api.pal.Platform.isInitialized()) {
            return null;
        }
        com.luatweaker.api.entity.IPlayer player =
                com.luatweaker.api.pal.Platform.getEntity().getPlayer(playerUuid);
        if (player == null) {
            return null;
        }
        return com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, player);
    }
}
