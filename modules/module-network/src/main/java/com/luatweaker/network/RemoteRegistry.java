package com.luatweaker.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Owns the RemoteEvent/RemoteFunction registries: lookup, creation from the
 * bootstrapped Lua classes, and the per-remote Lua service tables.
 */
final class RemoteRegistry {

    private final ILuaEngine engine;
    private final IRocketNetworkService sender;
    private final Map<String, ILuaTable> remoteEvents;
    private final Map<String, ILuaTable> remoteFunctions;

    /**
     * First mod to register each channel name (loading-mod id, or {@code "runtime"}
     * when created outside a load). Advisory for cross-VM traffic (a hostile client
     * can claim anything), exact for same-VM audit: tells the admin which mod
     * owns a channel in /lt doctor and the logs.
     */
    private final Map<String, String> channelOwners = new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Channel names that refuse inbound client traffic. A push-only channel
     * (server fires, client only listens) should block inbound so forged client
     * packets die before decode. Same policy backs events and functions.
     */
    private final InboundPolicy inbound = new InboundPolicy();

    private static final String RUNTIME_OWNER = "runtime";

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
                    RemoteServiceTables.create(engine, sender, inbound));
            recordOwner(name);
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
                    RemoteServiceTables.create(engine, sender, inbound));
            recordOwner(name);
            return res.asTable();
        });
    }

    /** First registrant wins; later mods sharing the name keep the original owner for audit. */
    private void recordOwner(String channelName) {
        String loadingMod = com.luatweaker.core.mod.LuaModContext.currentLoadingMod();
        String owner = loadingMod == null || loadingMod.isBlank() ? RUNTIME_OWNER : loadingMod;
        String previous = channelOwners.putIfAbsent(channelName, owner);
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network Registry] Channel '" + channelName + "' owned by '" + owner + "'"
                        + (previous == null || previous.equals(owner) ? "" : " (already owned by '"
                        + previous + "'; sharing for interop)"));
    }

    /** Channel name to owning mod id, for /lt doctor and admin audit. Never null values. */
    @NotNull
    Map<String, String> getChannelOwners() {
        return Map.copyOf(channelOwners);
    }

    /** Inbound switch shared with the dispatchers; they check it before decode. */
    @NotNull
    InboundPolicy inboundPolicy() {
        return inbound;
    }

    /** Java-side diagnostic only: names of all registered remote event channels. */
    @NotNull
    Set<String> getRegisteredRemoteEventNames() {
        return Set.copyOf(remoteEvents.keySet());
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
