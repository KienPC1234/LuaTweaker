package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Network service façade. All RemoteEvent/RemoteFunction logic lives in
 * same-package collaborators ({@link RemoteRegistry}, {@link PendingRpcTracker},
 * {@link RpcInvokeHandler}, {@link RemoteEventDispatcher}, {@link RateLimiter});
 * this class only owns the RPC surface plus the budget configuration and inherits
 * the RemoteEvent surface from {@link AbstractRemoteEventService}.
 */
public class NetworkServiceImpl extends AbstractRemoteEventService implements IRocketNetworkService {

    /** Channel that carries RemoteFunction request/response envelopes. */
    public static final String RPC_CHANNEL = "LuaTweaker:Rpc";
    /** Prefix reserved for engine channels; scripts must not register listeners there. */
    public static final String RESERVED_CHANNEL_PREFIX = "LuaTweaker:";

    /** Invoke flood protection: burst size per player. */
    static final double RPC_BURST = 60.0;
    /** Invoke flood protection: sustained requests per second per player. */
    static final double RPC_REFILL_PER_SECOND = 30.0;
    /** RemoteEvent flood protection: burst size per player for client-to-server events. */
    static final double EVENT_BURST = 120.0;
    /** RemoteEvent flood protection: sustained events per second per player. */
    static final double EVENT_REFILL_PER_SECOND = 60.0;
    /** Bucket key for the client's own outbound RemoteEvents (there is one connection). */
    static final String LOCAL_CLIENT_BUDGET_KEY = "local-client";

    private final RemoteRegistry registry;
    private final PendingRpcTracker tracker;
    private final RpcInvokeHandler rpcHandler;

    public NetworkServiceImpl(ILuaEngine engine) {
        super(engine);
        this.registry = new RemoteRegistry(engine, this, remoteEvents, remoteFunctions);
        this.tracker = new PendingRpcTracker(engine);
        this.rpcHandler = new RpcInvokeHandler(engine, remoteFunctions,
                rateLimiter, RPC_BURST, RPC_REFILL_PER_SECOND);
    }

    @Override
    public ILuaTable GetOrCreateRemoteEvent(String channelName) {
        return registry.getOrCreateRemoteEvent(channelName);
    }

    @Override
    public ILuaTable GetOrCreateRemoteFunction(String functionName) {
        return registry.getOrCreateRemoteFunction(functionName);
    }

    /** Java-side diagnostic only: names of all registered remote event channels. */
    public @NotNull Set<String> getRegisteredRemoteEventNames() {
        return registry.getRegisteredRemoteEventNames();
    }

    // ==== RemoteFunction request/response ====

    @Override
    public String RpcInvokeServer(String functionName, ILuaValue... args) {
        rpcHandler.requireRegisteredFunction(functionName);
        String requestId = tracker.registerRequest();
        boolean sent = false;
        try {
            byte[] envelope = RpcInvokeHandler.encodeInvoke(requestId, functionName, args);
            Platform.getNetwork().sendPayloadBytesToServer(RPC_CHANNEL, envelope);
            sent = true;
        } finally {
            if (!sent) {
                tracker.removeRequest(requestId);
            }
        }
        return requestId;
    }

    @Override
    public String RpcInvokeClient(String functionName, String playerUuid, ILuaValue... args) {
        rpcHandler.requireRegisteredFunction(functionName);
        if (playerUuid == null || playerUuid.isBlank()) {
            throw new IllegalArgumentException(
                    "RemoteFunction:InvokeClient() requires a player (string uuid or Player object)");
        }
        String requestId = tracker.registerRequest();
        boolean sent = false;
        try {
            byte[] envelope = RpcInvokeHandler.encodeInvoke(requestId, functionName, args);
            Platform.getNetwork().sendPayloadBytes(playerUuid, RPC_CHANNEL, envelope);
            sent = true;
        } finally {
            if (!sent) {
                tracker.removeRequest(requestId);
            }
        }
        return requestId;
    }

    @Override
    public ILuaTable RpcPoll(String requestId) {
        return tracker.poll(requestId);
    }

    @Override
    public void RpcCancel(String requestId) {
        tracker.cancel(requestId);
    }

    @Override
    public void OnRpcInvoke(String requestId, String functionName, String playerUuid, byte[] argsData) {
        rpcHandler.invoke(requestId, functionName, playerUuid, argsData);
    }

    @Override
    public void OnRpcResult(String requestId, byte[] payload, String error) {
        tracker.complete(requestId, payload, error);
    }
}
