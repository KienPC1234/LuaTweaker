package com.luatweaker.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Inbound RemoteEvent dispatch to Lua {@code OnServerEvent}/{@code OnClientEvent} listeners. */
final class RemoteEventDispatcher {

    private final ILuaEngine engine;
    private final Map<String, ILuaTable> remoteEvents;
    private final RateLimiter rateLimiter;
    private final double eventBurst;
    private final double eventRefillPerSecond;
    private final double channelBurst;
    private final double channelRefillPerSecond;
    private final InboundPolicy inboundPolicy;

    RemoteEventDispatcher(ILuaEngine engine, Map<String, ILuaTable> remoteEvents,
                          RateLimiter rateLimiter, double eventBurst, double eventRefillPerSecond,
                          double channelBurst, double channelRefillPerSecond,
                          InboundPolicy inboundPolicy) {
        this.engine = engine;
        this.remoteEvents = remoteEvents;
        this.rateLimiter = rateLimiter;
        this.eventBurst = eventBurst;
        this.eventRefillPerSecond = eventRefillPerSecond;
        this.channelBurst = channelBurst;
        this.channelRefillPerSecond = channelRefillPerSecond;
        this.inboundPolicy = inboundPolicy;
    }

    void onClientFired(String channelName, String playerUuid, byte[] data) {
        if (!isServerboundAllowed(channelName, playerUuid)) {
            return;
        }
        ILuaValue onServerEvent = lookupServerListener(channelName);
        if (onServerEvent == null) {
            return;
        }
        ILuaValue fireFn = resolveFireSync();
        if (fireFn == null) {
            return;
        }
        ILuaValue player = resolveFirePlayer(playerUuid);
        if (player == null) {
            return;
        }
        ILuaValue[] args = decodeFireArgs(channelName, data);
        if (args == null) {
            return;
        }
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] OnClientFired firing RemoteEvent '" + channelName
                        + "' for player: " + playerUuid);
        ILuaValue[] fireArgs = new ILuaValue[args.length + 1];
        fireArgs[0] = player;
        System.arraycopy(args, 0, fireArgs, 1, args.length);
        engine.callFunction(fireFn, appendThis(onServerEvent, fireArgs));
    }

    private boolean isServerboundAllowed(String channelName, String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped serverbound RemoteEvent '" + channelName + "': no player identity");
            return false;
        }
        if (inboundPolicy.isBlocked(channelName)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped serverbound RemoteEvent '" + channelName
                            + "': inbound blocked by the owning mod");
            return false;
        }
        if (!tryConsumeChannelBudget(playerUuid, channelName)) {
            return false;
        }
        if (!rateLimiter.tryConsumeEvent(playerUuid, eventBurst, eventRefillPerSecond)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited RemoteEvent '" + channelName + "' from player " + playerUuid);
            return false;
        }
        return true;
    }

    /**
     * Per-channel budget, checked before the shared per-player budget so one
     * chatty mod throttles itself instead of starving the player's other mods.
     * Unknown channel names skip this (they must not mint unlimited buckets)
     * and still pay the per-player budget below.
     */
    private boolean tryConsumeChannelBudget(String playerUuid, String channelName) {
        if (channelName == null || !remoteEvents.containsKey(channelName)) {
            return true;
        }
        if (rateLimiter.tryConsumeEvent(channelKey(playerUuid, channelName),
                channelBurst, channelRefillPerSecond)) {
            return true;
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "[Network] Rate-limited RemoteEvent '" + channelName
                        + "' on its own channel budget; other channels are unaffected");
        return false;
    }

    /**
     * Bucket key joining player and channel with a separator that cannot occur
     * in a player UUID (canonical UUIDs never contain NUL), so distinct
     * (player, channel) pairs can never alias the same bucket.
     */
    private static String channelKey(String playerUuid, String channelName) {
        return playerUuid + '\0' + channelName;
    }

    private @Nullable ILuaValue lookupServerListener(String channelName) {
        ILuaTable remoteEvent = remoteEvents.get(channelName);
        if (remoteEvent == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network Error] RemoteEvent '" + channelName
                            + "' was NOT registered in remoteEvents map! Registered channels: "
                            + remoteEvents.keySet());
            return null;
        }
        ILuaValue onServerEvent = remoteEvent.rawget("OnServerEvent");
        if (onServerEvent == null || onServerEvent.isNil()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network Error] RemoteEvent '" + channelName + "' exists but OnServerEvent is NIL!");
            return null;
        }
        return onServerEvent;
    }

    private @Nullable ILuaValue resolveFirePlayer(String playerUuid) {
        if (!Platform.isInitialized()) {
            return engine.nilValue();
        }
        ILuaValue resolved = RemoteRegistry.resolvePlayerTable(engine, playerUuid);
        if (resolved == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network Error] OnClientFired aborted: Player UUID '" + playerUuid
                            + "' not found on server.");
            return null;
        }
        return resolved;
    }

    private @Nullable ILuaValue[] decodeFireArgs(String channelName, byte[] data) {
        try {
            return LuaPayloadCodec.decodeArgsOrEmpty(engine, data);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped RemoteEvent '" + channelName + "' with malformed payload: "
                            + e.getMessage());
            return null;
        }
    }

    void onServerFired(String channelName, String playerUuid, byte[] data) {
        ILuaTable remoteEvent = remoteEvents.get(channelName);
        if (remoteEvent == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network] Clientbound RemoteEvent '" + channelName
                            + "' has no listener on this client. Registered channels: "
                            + remoteEvents.keySet());
            return;
        }
        ILuaValue onClientEvent = remoteEvent.rawget("OnClientEvent");
        if (onClientEvent == null || onClientEvent.isNil()) {
            return;
        }
        ILuaValue fireFn = resolveFireSync();
        if (fireFn == null) {
            return;
        }
        ILuaValue[] args = decodeFireArgs(channelName, data);
        if (args == null) {
            return;
        }
        engine.callFunction(fireFn, appendThis(onClientEvent, args));
    }

    private @Nullable ILuaValue resolveFireSync() {
        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        if (signalClass == null || !signalClass.isTable()) {
            return null;
        }
        ILuaValue fireFn = signalClass.asTable().rawget("FireSync");
        if (fireFn == null || fireFn.isNil()) {
            fireFn = signalClass.asTable().rawget("Fire");
        }
        return fireFn == null || fireFn.isNil() ? null : fireFn;
    }

    private ILuaValue[] appendThis(ILuaValue self, ILuaValue... args) {
        ILuaValue[] result = new ILuaValue[args.length + 1];
        result[0] = self;
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }
}
