package com.luatweaker.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * RemoteEvent delivery: outbound sends plus inbound dispatch to the Lua
 * {@code OnServerEvent}/{@code OnClientEvent} listeners through a synchronous
 * signal fire.
 */
final class RemoteEventDispatcher {

    private final ILuaEngine engine;
    private final Map<String, ILuaTable> remoteEvents;
    private final RateLimiter rateLimiter;
    private final double eventBurst;
    private final double eventRefillPerSecond;
    private final String localClientBudgetKey;

    RemoteEventDispatcher(ILuaEngine engine, Map<String, ILuaTable> remoteEvents,
                          RateLimiter rateLimiter, double eventBurst, double eventRefillPerSecond,
                          String localClientBudgetKey) {
        this.engine = engine;
        this.remoteEvents = remoteEvents;
        this.rateLimiter = rateLimiter;
        this.eventBurst = eventBurst;
        this.eventRefillPerSecond = eventRefillPerSecond;
        this.localClientBudgetKey = localClientBudgetKey;
    }

    void fireClient(String channelName, String playerUuid, ILuaValue... args) {
        Platform.getNetwork().sendPayloadBytes(playerUuid, channelName,
                LuaPayloadCodec.encodeArgs(args == null ? new ILuaValue[0] : args));
    }

    void fireAllClients(String channelName, ILuaValue... args) {
        Platform.getNetwork().broadcastPayloadBytes(channelName,
                LuaPayloadCodec.encodeArgs(args == null ? new ILuaValue[0] : args));
    }

    void fireServer(String channelName, ILuaValue... args) {
        // Client-side budget: a runaway loop should not fill the socket with packets the
        // server is going to drop anyway. Same numbers as the server-side check.
        if (!rateLimiter.tryConsumeEvent(localClientBudgetKey, eventBurst, eventRefillPerSecond)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited outbound RemoteEvent '" + channelName + "'");
            return;
        }
        Platform.getNetwork().sendPayloadBytesToServer(channelName,
                LuaPayloadCodec.encodeArgs(args == null ? new ILuaValue[0] : args));
    }

    void onClientFired(String channelName, String playerUuid, byte[] data) {
        if (playerUuid == null || playerUuid.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped serverbound RemoteEvent '" + channelName + "': no player identity");
            return;
        }
        // Budget before decode: an unknown channel must not turn into free parsing work.
        if (!rateLimiter.tryConsumeEvent(playerUuid, eventBurst, eventRefillPerSecond)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited RemoteEvent '" + channelName + "' from player " + playerUuid);
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
        ILuaValue[] args = decodeFireArgs(channelName, data).orElse(null);
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

    private Optional<ILuaValue[]> decodeFireArgs(String channelName, byte[] data) {
        try {
            return Optional.of(LuaPayloadCodec.decodeArgsOrEmpty(engine, data));
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped RemoteEvent '" + channelName + "' with malformed payload: "
                            + e.getMessage());
            return Optional.empty();
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
        ILuaValue[] args;
        try {
            args = LuaPayloadCodec.decodeArgsOrEmpty(engine, data);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped RemoteEvent '" + channelName + "' with malformed payload: "
                            + e.getMessage());
            return;
        }
        engine.callFunction(fireFn, appendThis(onClientEvent, args));
    }

    /**
     * Network handlers mutate world/player state and must run synchronously on the calling
     * (main) thread, never on a deferred coroutine virtual thread.
     */
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
