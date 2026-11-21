package com.luatweaker.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

import java.util.Map;
import java.util.Optional;

/**
 * Server/client handling of RemoteFunction invocations: validation, flood
 * protection, handler lookup, argument decoding, Lua invocation and answering.
 */
final class RpcInvokeHandler {

    private static final byte[] EMPTY_PAYLOAD = LuaPayloadCodec.encodeArgs(new ILuaValue[0]);
    private static final String REMOTE_FN_QUOTE = "RemoteFunction '";

    private final ILuaEngine engine;
    private final Map<String, ILuaTable> remoteFunctions;
    private final RateLimiter rateLimiter;
    private final double rpcBurst;
    private final double rpcRefillPerSecond;
    private final double rpcChannelBurst;
    private final double rpcChannelRefillPerSecond;
    private final InboundPolicy inboundPolicy;

    RpcInvokeHandler(ILuaEngine engine, Map<String, ILuaTable> remoteFunctions,
                     RateLimiter rateLimiter,
                     double rpcBurst, double rpcRefillPerSecond,
                     double rpcChannelBurst, double rpcChannelRefillPerSecond,
                     InboundPolicy inboundPolicy) {
        this.engine = engine;
        this.remoteFunctions = remoteFunctions;
        this.rateLimiter = rateLimiter;
        this.rpcBurst = rpcBurst;
        this.rpcRefillPerSecond = rpcRefillPerSecond;
        this.rpcChannelBurst = rpcChannelBurst;
        this.rpcChannelRefillPerSecond = rpcChannelRefillPerSecond;
        this.inboundPolicy = inboundPolicy;
    }

    void invoke(String requestId, String functionName, String playerUuid, byte[] argsData) {
        boolean fromClient = playerUuid != null && !playerUuid.isBlank();
        if (!validateRpcRequest(requestId, functionName)) {
            return;
        }

        if (fromClient && !isServerboundAllowed(requestId, functionName, playerUuid)) {
            return;
        }

        ILuaValue callback = lookupRpcCallback(requestId, fromClient, playerUuid, functionName);
        if (callback == null) {
            return;
        }

        ILuaValue[] args = decodeRpcArgs(requestId, fromClient, playerUuid, functionName, argsData).orElse(null);
        if (args == null) {
            return;
        }

        ILuaValue result = invokeRpcCallback(requestId, fromClient, playerUuid, functionName, callback, args);
        if (result == null) {
            return;
        }

        byte[] resultPayload = encodeRpcResult(requestId, fromClient, playerUuid, functionName, result).orElse(null);
        if (resultPayload == null) {
            return;
        }
        respondRpc(requestId, fromClient, playerUuid, functionName, resultPayload, "");
    }

    private boolean isServerboundAllowed(String requestId, String functionName, String playerUuid) {
        if (inboundPolicy.isBlocked(functionName)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped serverbound RemoteFunction '" + functionName
                            + "': inbound blocked by the owning mod");
            respondRpc(requestId, true, playerUuid, functionName, null,
                    REMOTE_FN_QUOTE + functionName + "' does not accept client requests");
            return false;
        }
        if (remoteFunctions.containsKey(functionName)
                && !rateLimiter.tryConsumeRpc(playerUuid + '\0' + functionName,
                        rpcChannelBurst, rpcChannelRefillPerSecond)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited RemoteFunction '" + functionName
                            + "' on its own function budget; other functions are unaffected");
            respondRpc(requestId, true, playerUuid, functionName, null,
                    "Too many RemoteFunction requests for '" + functionName + "'; slow down");
            return false;
        }
        // Rate-limit before anything else: unknown function names must not bypass the bucket
        // and turn the server into an error-packet amplifier.
        if (!rateLimiter.tryConsumeRpc(playerUuid, rpcBurst, rpcRefillPerSecond)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited RemoteFunction '" + functionName + "' from player " + playerUuid);
            respondRpc(requestId, true, playerUuid, functionName, null,
                    "Too many RemoteFunction requests; slow down");
            return false;
        }
        return true;
    }

    private boolean validateRpcRequest(String requestId, String functionName) {
        if (requestId == null || requestId.isBlank() || functionName == null || functionName.isBlank()) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped malformed RemoteFunction request (id='" + requestId
                            + "', function='" + functionName + "')");
            return false;
        }
        return true;
    }

    private @Nullable ILuaValue lookupRpcCallback(String requestId, boolean fromClient,
                                                   String playerUuid, String functionName) {
        ILuaTable remoteFn = remoteFunctions.get(functionName);
        String handlerName = fromClient ? "OnServerInvoke" : "OnClientInvoke";
        ILuaValue callback = remoteFn == null ? null : remoteFn.rawget(handlerName);
        if (callback == null || !callback.isFunction()) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    REMOTE_FN_QUOTE + functionName + "' has no " + handlerName + " handler");
            return null;
        }
        return callback;
    }

    private Optional<ILuaValue[]> decodeRpcArgs(String requestId, boolean fromClient,
                                                String playerUuid, String functionName, byte... argsData) {
        try {
            return Optional.of(LuaPayloadCodec.decodeArgsOrEmpty(engine, argsData));
        } catch (IllegalArgumentException e) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    "Malformed RemoteFunction request payload: " + e.getMessage());
            return Optional.empty();
        }
    }

    private @Nullable ILuaValue invokeRpcCallback(String requestId, boolean fromClient, String playerUuid,
                                                   String functionName, ILuaValue callback, ILuaValue... args) {
        try {
            ILuaValue[] callArgs = args;
            if (fromClient) {
                ILuaValue player = RemoteRegistry.resolvePlayerTable(engine, playerUuid);
                if (player == null) {
                    respondRpc(requestId, fromClient, playerUuid, functionName, null,
                            "Player '" + playerUuid + "' is no longer online");
                    return null;
                }
                callArgs = new ILuaValue[args.length + 1];
                callArgs[0] = player;
                System.arraycopy(args, 0, callArgs, 1, args.length);
            }
            return engine.callFunction(callback, callArgs);
        } catch (LuaException e) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    REMOTE_FN_QUOTE + functionName + "' raised: " + e.getMessage());
            return null;
        }
    }

    private Optional<byte[]> encodeRpcResult(String requestId, boolean fromClient, String playerUuid,
                                               String functionName, ILuaValue result) {
        try {
            return Optional.of(LuaPayloadCodec.encodeArgs(result));
        } catch (IllegalArgumentException e) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    "Return value of RemoteFunction '" + functionName + "' cannot be sent: " + e.getMessage());
            return Optional.empty();
        }
    }

    void respondRpc(String requestId, boolean toClient, String playerUuid, String functionName,
                    @Nullable byte[] payload, @Nullable String error) {
        byte[] safePayload = payload == null ? EMPTY_PAYLOAD : payload;
        byte[] envelope;
        try {
            envelope = new RpcEnvelope(requestId, RpcEnvelope.PHASE_RESULT, functionName,
                    RpcEnvelope.truncateError(error), safePayload).encode();
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Cannot build RemoteFunction answer for '" + functionName + "': "
                            + e.getMessage());
            return;
        }
        // Best-effort delivery: the platform transport guards its own failures
        // (unknown player, null payload); a throwing transport is a platform
        // bug and must surface instead of being logged away here.
        if (toClient) {
            Platform.getNetwork().sendPayloadBytes(playerUuid, NetworkServiceImpl.RPC_CHANNEL, envelope);
        } else {
            Platform.getNetwork().sendPayloadBytesToServer(NetworkServiceImpl.RPC_CHANNEL, envelope);
        }
    }

    static byte[] encodeInvoke(String requestId, String functionName, ILuaValue... args) {
        byte[] payload = LuaPayloadCodec.encodeArgs(args == null ? new ILuaValue[0] : args);
        return new RpcEnvelope(requestId, RpcEnvelope.PHASE_INVOKE, functionName, "", payload).encode();
    }

    void requireRegisteredFunction(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("RemoteFunction name must not be empty");
        }
        if (!remoteFunctions.containsKey(functionName)) {
            throw new IllegalArgumentException(REMOTE_FN_QUOTE + functionName
                    + "' is not registered on this side; call NetworkService:GetOrCreateRemoteFunction('"
                    + functionName + "') first");
        }
    }
}
