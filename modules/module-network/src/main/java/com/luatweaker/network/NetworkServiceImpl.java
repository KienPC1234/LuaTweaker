package com.luatweaker.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NetworkServiceImpl implements IRocketNetworkService {

    /** Channel that carries RemoteFunction request/response envelopes. */
    public static final String RPC_CHANNEL = "LuaTweaker:Rpc";
    /** Prefix reserved for engine channels; scripts must not register listeners there. */
    public static final String RESERVED_CHANNEL_PREFIX = "LuaTweaker:";

    /** Upper bound on outstanding request/response round trips in this VM. */
    private static final int MAX_PENDING_RPC = 256;
    /** A request nobody polls (dead coroutine, reload) is dropped after this. */
    private static final long PENDING_RPC_TTL_NANOS = TimeUnit.SECONDS.toNanos(60);
    /** Invoke flood protection: burst size per player. */
    private static final double RPC_BURST = 60.0;
    /** Invoke flood protection: sustained requests per second per player. */
    private static final double RPC_REFILL_PER_SECOND = 30.0;
    /** RemoteEvent flood protection: burst size per player for client-to-server events. */
    static final double EVENT_BURST = 120.0;
    /** RemoteEvent flood protection: sustained events per second per player. */
    static final double EVENT_REFILL_PER_SECOND = 60.0;
    /** Number of per-player buckets kept before idle ones are evicted. */
    private static final int MAX_BUDGET_ENTRIES = 4_096;
    /** Bucket key for the client's own outbound RemoteEvents (there is one connection). */
    private static final String LOCAL_CLIENT_BUDGET_KEY = "local-client";

    private static final ILuaValue[] NO_ARGS = new ILuaValue[0];
    /** Wire form of "no arguments"; used for error answers that carry no value. */
    private static final byte[] EMPTY_PAYLOAD = LuaPayloadCodec.encodeArgs(NO_ARGS);

    private final ILuaEngine engine;
    private final Map<String, ILuaTable> remoteEvents = new ConcurrentHashMap<>();
    private final Map<String, ILuaTable> remoteFunctions = new ConcurrentHashMap<>();
    private final Map<String, PendingRpc> pendingRpcs = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> rpcBudgets = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> eventBudgets = new ConcurrentHashMap<>();

    public NetworkServiceImpl(ILuaEngine engine) {
        this.engine = engine;
    }

    private ILuaTable createServiceTable() {
        ILuaTable table = engine.createTable();
        table.rawset("FireServer", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 2) {
                String name = args[off].asString();
                ILuaValue[] passArgs = args[off + 1].isTable() ? unpackTable(args[off + 1].asTable()) : new ILuaValue[0];
                this.FireServer(name, passArgs);
            }
            return engine.nilValue();
        });
        table.rawset("FireClient", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 3) {
                String name = args[off].asString();
                String uuid = args[off + 1].asString();
                ILuaValue[] passArgs = args[off + 2].isTable() ? unpackTable(args[off + 2].asTable()) : new ILuaValue[0];
                this.FireClient(name, uuid, passArgs);
            }
            return engine.nilValue();
        });
        table.rawset("FireAllClients", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 2) {
                String name = args[off].asString();
                ILuaValue[] passArgs = args[off + 1].isTable() ? unpackTable(args[off + 1].asTable()) : new ILuaValue[0];
                this.FireAllClients(name, passArgs);
            }
            return engine.nilValue();
        });
        return table;
    }

    /**
     * Unpacks a Lua vararg-style table into a positional array, preserving order.
     * (asMap() is a HashMap whose iteration order scrambles positional args.)
     */
    static ILuaValue[] unpackTable(ILuaTable tbl) {
        java.util.ArrayList<ILuaValue> list = new java.util.ArrayList<>();
        for (int i = 1; i <= tbl.length(); i++) {
            ILuaValue val = tbl.rawget(i);
            if (val == null || val.isNil()) break;
            list.add(val);
        }
        return list.toArray(new ILuaValue[0]);
    }

    @Override
    public ILuaTable GetOrCreateRemoteEvent(String channelName) {
        requireScriptChannelName(channelName, "RemoteEvent");
        ILuaTable resTable = remoteEvents.computeIfAbsent(channelName, name -> {
            ILuaValue remoteEventClass = engine.getGlobalEnvironment().rawget("RemoteEvent");
            if (remoteEventClass == null || remoteEventClass.isNil()) {
                throw new IllegalStateException("RemoteEvent Lua class not initialized. Did bootstrap run?");
            }
            ILuaValue newFn = remoteEventClass.asTable().rawget("new");
            ILuaValue res = engine.callFunction(newFn, remoteEventClass, engine.wrapString(name), createServiceTable());
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network Registry] [NetService@" + System.identityHashCode(this)
                            + " / Engine@" + System.identityHashCode(engine) + "] Registered RemoteEvent '"
                            + channelName + "'. All channels in this instance: " + remoteEvents.keySet());
            return res.asTable();
        });
        return resTable;
    }

    @Override
    public ILuaTable GetOrCreateRemoteFunction(String functionName) {
        requireScriptChannelName(functionName, "RemoteFunction");
        return remoteFunctions.computeIfAbsent(functionName, name -> {
            ILuaValue remoteFnClass = engine.getGlobalEnvironment().rawget("RemoteFunction");
            if (remoteFnClass == null || remoteFnClass.isNil()) {
                throw new IllegalStateException("RemoteFunction Lua class not initialized. Did bootstrap run?");
            }
            ILuaValue newFn = remoteFnClass.asTable().rawget("new");
            ILuaValue res = engine.callFunction(newFn, remoteFnClass, engine.wrapString(name), createServiceTable());
            return res.asTable();
        });
    }

    /** Java-side diagnostic only: names of all registered remote event channels. */
    public java.util.Set<String> getRegisteredRemoteEventNames() {
        return java.util.Set.copyOf(remoteEvents.keySet());
    }

    // ==== RemoteFunction request/response ====

    @Override
    public String RpcInvokeServer(String functionName, ILuaValue[] args) {
        requireRegisteredFunction(functionName);
        String requestId = registerRequest();
        try {
            byte[] envelope = encodeInvoke(requestId, functionName, args);
            Platform.getNetwork().sendPayloadBytesToServer(RPC_CHANNEL, envelope);
        } catch (RuntimeException e) {
            pendingRpcs.remove(requestId);
            throw e;
        }
        return requestId;
    }

    @Override
    public String RpcInvokeClient(String functionName, String playerUuid, ILuaValue[] args) {
        requireRegisteredFunction(functionName);
        if (playerUuid == null || playerUuid.isBlank()) {
            throw new IllegalArgumentException(
                    "RemoteFunction:InvokeClient() requires a player (string uuid or Player object)");
        }
        String requestId = registerRequest();
        try {
            byte[] envelope = encodeInvoke(requestId, functionName, args);
            Platform.getNetwork().sendPayloadBytes(playerUuid, RPC_CHANNEL, envelope);
        } catch (RuntimeException e) {
            pendingRpcs.remove(requestId);
            throw e;
        }
        return requestId;
    }

    private static byte[] encodeInvoke(String requestId, String functionName, ILuaValue[] args) {
        byte[] payload = LuaPayloadCodec.encodeArgs(args == null ? NO_ARGS : args);
        return new RpcEnvelope(requestId, RpcEnvelope.PHASE_INVOKE, functionName, "", payload).encode();
    }

    @Override
    public ILuaTable RpcPoll(String requestId) {
        ILuaTable result = engine.createTable();
        PendingRpc pending = requestId == null ? null : pendingRpcs.get(requestId);
        if (pending == null) {
            result.rawset("Status", engine.wrapString("error"));
            result.rawset("Error", engine.wrapString("RemoteFunction request is unknown or already completed"));
            return result;
        }
        if (pending.state == RpcState.PENDING
                && System.nanoTime() - pending.createdAtNanos > PENDING_RPC_TTL_NANOS) {
            pendingRpcs.remove(requestId);
            result.rawset("Status", engine.wrapString("error"));
            result.rawset("Error", engine.wrapString("RemoteFunction request expired before a response arrived"));
            return result;
        }
        switch (pending.state) {
            case PENDING -> result.rawset("Status", engine.wrapString("pending"));
            case DONE -> {
                // One-shot: the Lua wrapper consumes the value exactly once.
                pendingRpcs.remove(requestId);
                result.rawset("Status", engine.wrapString("done"));
                result.rawset("Value", pending.value != null ? pending.value : engine.nilValue());
            }
            case ERROR -> {
                pendingRpcs.remove(requestId);
                result.rawset("Status", engine.wrapString("error"));
                result.rawset("Error", engine.wrapString(
                        pending.error != null ? pending.error : "RemoteFunction call failed"));
            }
        }
        return result;
    }

    @Override
    public void RpcCancel(String requestId) {
        if (requestId != null) {
            pendingRpcs.remove(requestId);
        }
    }

    @Override
    public void OnRpcInvoke(String requestId, String functionName, String playerUuid, byte[] argsData) {
        boolean fromClient = playerUuid != null && !playerUuid.isBlank();
        if (requestId == null || requestId.isBlank() || functionName == null || functionName.isBlank()) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped malformed RemoteFunction request (id='" + requestId
                            + "', function='" + functionName + "')");
            return;
        }

        // Rate-limit before anything else: unknown function names must not bypass the bucket
        // and turn the server into an error-packet amplifier.
        if (fromClient && !consumeBudget(rpcBudgets, playerUuid, RPC_BURST, RPC_REFILL_PER_SECOND)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited RemoteFunction '" + functionName + "' from player " + playerUuid);
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    "Too many RemoteFunction requests; slow down");
            return;
        }

        ILuaTable remoteFn = remoteFunctions.get(functionName);
        String handlerName = fromClient ? "OnServerInvoke" : "OnClientInvoke";
        ILuaValue callback = remoteFn == null ? null : remoteFn.rawget(handlerName);
        if (callback == null || !callback.isFunction()) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    "RemoteFunction '" + functionName + "' has no " + handlerName + " handler");
            return;
        }

        ILuaValue[] args;
        try {
            args = decodeArgs(argsData);
        } catch (RuntimeException e) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    "Malformed RemoteFunction request payload: " + e.getMessage());
            return;
        }

        ILuaValue result;
        try {
            ILuaValue[] callArgs = args;
            if (fromClient) {
                ILuaValue player = resolvePlayerTable(playerUuid);
                if (player == null) {
                    respondRpc(requestId, fromClient, playerUuid, functionName, null,
                            "Player '" + playerUuid + "' is no longer online");
                    return;
                }
                callArgs = new ILuaValue[args.length + 1];
                callArgs[0] = player;
                System.arraycopy(args, 0, callArgs, 1, args.length);
            }
            result = engine.callFunction(callback, callArgs);
        } catch (RuntimeException e) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    "RemoteFunction '" + functionName + "' raised: " + e.getMessage());
            return;
        }

        byte[] resultPayload;
        try {
            resultPayload = LuaPayloadCodec.encodeArgs(new ILuaValue[]{result});
        } catch (RuntimeException e) {
            respondRpc(requestId, fromClient, playerUuid, functionName, null,
                    "Return value of RemoteFunction '" + functionName + "' cannot be sent: " + e.getMessage());
            return;
        }
        respondRpc(requestId, fromClient, playerUuid, functionName, resultPayload, "");
    }

    @Override
    public void OnRpcResult(String requestId, byte[] payload, String error) {
        PendingRpc pending = requestId == null ? null : pendingRpcs.get(requestId);
        if (pending == null) {
            // Late answer for a timed-out/cancelled request, or a spoofed id.
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network] Ignored RemoteFunction response for unknown request " + requestId);
            return;
        }
        if (pending.state != RpcState.PENDING) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Network] Ignored duplicate RemoteFunction response " + requestId);
            return;
        }
        if (error != null && !error.isEmpty()) {
            pending.error = error;
            pending.state = RpcState.ERROR;
            return;
        }
        try {
            ILuaValue[] values = decodeArgs(payload);
            pending.value = values.length > 0 ? values[0] : engine.nilValue();
            pending.state = RpcState.DONE;
        } catch (RuntimeException e) {
            pending.error = "Malformed RemoteFunction response payload: " + e.getMessage();
            pending.state = RpcState.ERROR;
        }
    }

    /**
     * Engine channels ({@code LuaTweaker:*}) are routed before RemoteEvents, so a script
     * listener registered under one would either shadow a control command or be silently
     * unreachable. Reject it loudly.
     */
    private static void requireScriptChannelName(@Nullable String name, @NotNull String kind) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(kind + " name must not be empty");
        }
        if (name.startsWith(RESERVED_CHANNEL_PREFIX)) {
            throw new IllegalArgumentException(kind + " name '" + name + "' uses the reserved '"
                    + RESERVED_CHANNEL_PREFIX + "' prefix");
        }
    }

    private void requireRegisteredFunction(String functionName) {
        if (functionName == null || functionName.isBlank()) {
            throw new IllegalArgumentException("RemoteFunction name must not be empty");
        }
        if (!remoteFunctions.containsKey(functionName)) {
            throw new IllegalArgumentException("RemoteFunction '" + functionName
                    + "' is not registered on this side; call NetworkService:GetOrCreateRemoteFunction('"
                    + functionName + "') first");
        }
    }

    private String registerRequest() {
        pruneExpiredRequests();
        if (pendingRpcs.size() >= MAX_PENDING_RPC) {
            throw new IllegalStateException("Too many unanswered RemoteFunction requests ("
                    + MAX_PENDING_RPC + "); responses were never polled. Use RpcCancel on timeout.");
        }
        String requestId = UUID.randomUUID().toString();
        pendingRpcs.put(requestId, new PendingRpc());
        return requestId;
    }

    private void pruneExpiredRequests() {
        long now = System.nanoTime();
        pendingRpcs.entrySet().removeIf(entry ->
                entry.getValue().state == RpcState.PENDING
                        && now - entry.getValue().createdAtNanos > PENDING_RPC_TTL_NANOS);
    }

    private boolean consumeBudget(Map<String, TokenBucket> budgets, String key,
                                  double burst, double refillPerSecond) {
        if (budgets.size() > MAX_BUDGET_ENTRIES) {
            budgets.clear();
        }
        return budgets.computeIfAbsent(key, ignored -> new TokenBucket(burst, refillPerSecond)).tryConsume();
    }

    private @Nullable ILuaValue resolvePlayerTable(String playerUuid) {
        if (!Platform.isInitialized()) {
            return null;
        }
        com.luatweaker.api.entity.IPlayer player = Platform.getEntity().getPlayer(playerUuid);
        if (player == null) {
            return null;
        }
        return com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, player);
    }

    private void respondRpc(String requestId, boolean toClient, String playerUuid, String functionName,
                            @Nullable byte[] payload, @Nullable String error) {
        byte[] safePayload = payload == null ? EMPTY_PAYLOAD : payload;
        byte[] envelope;
        try {
            envelope = new RpcEnvelope(requestId, RpcEnvelope.PHASE_RESULT, functionName,
                    RpcEnvelope.truncateError(error), safePayload).encode();
        } catch (RuntimeException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Cannot build RemoteFunction answer for '" + functionName + "': "
                            + e.getMessage());
            return;
        }
        try {
            if (toClient) {
                Platform.getNetwork().sendPayloadBytes(playerUuid, RPC_CHANNEL, envelope);
            } else {
                Platform.getNetwork().sendPayloadBytesToServer(RPC_CHANNEL, envelope);
            }
        } catch (RuntimeException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Failed to answer RemoteFunction '" + functionName + "': " + e.getMessage());
        }
    }

    // ==== RemoteEvents ====

    @Override
    public void FireClient(String channelName, String playerUuid, ILuaValue[] args) {
        Platform.getNetwork().sendPayloadBytes(playerUuid, channelName,
                LuaPayloadCodec.encodeArgs(args == null ? NO_ARGS : args));
    }

    @Override
    public void FireAllClients(String channelName, ILuaValue[] args) {
        Platform.getNetwork().broadcastPayloadBytes(channelName,
                LuaPayloadCodec.encodeArgs(args == null ? NO_ARGS : args));
    }

    @Override
    public void FireServer(String channelName, ILuaValue[] args) {
        // Client-side budget: a runaway loop should not fill the socket with packets the
        // server is going to drop anyway. Same numbers as the server-side check.
        if (!consumeBudget(eventBudgets, LOCAL_CLIENT_BUDGET_KEY, EVENT_BURST, EVENT_REFILL_PER_SECOND)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited outbound RemoteEvent '" + channelName + "'");
            return;
        }
        Platform.getNetwork().sendPayloadBytesToServer(channelName,
                LuaPayloadCodec.encodeArgs(args == null ? NO_ARGS : args));
    }

    @Override
    public void OnClientFired(String channelName, String playerUuid, byte[] data) {
        if (playerUuid == null || playerUuid.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped serverbound RemoteEvent '" + channelName + "': no player identity");
            return;
        }
        // Budget before decode: an unknown channel must not turn into free parsing work.
        if (!consumeBudget(eventBudgets, playerUuid, EVENT_BURST, EVENT_REFILL_PER_SECOND)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Rate-limited RemoteEvent '" + channelName + "' from player " + playerUuid);
            return;
        }
        ILuaTable remoteEvent = remoteEvents.get(channelName);
        if (remoteEvent == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network Error] RemoteEvent '" + channelName
                            + "' was NOT registered in remoteEvents map! Registered channels: "
                            + remoteEvents.keySet());
            return;
        }
        ILuaValue onServerEvent = remoteEvent.rawget("OnServerEvent");
        if (onServerEvent == null || onServerEvent.isNil()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network Error] RemoteEvent '" + channelName + "' exists but OnServerEvent is NIL!");
            return;
        }
        ILuaValue fireFn = resolveFireSync();
        if (fireFn == null) {
            return;
        }
        ILuaValue player = engine.nilValue();
        com.luatweaker.api.entity.IPlayer resolved = null;
        if (Platform.isInitialized()) {
            resolved = Platform.getEntity().getPlayer(playerUuid);
            if (resolved == null) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Network Error] OnClientFired aborted: Player UUID '" + playerUuid
                                + "' not found on server.");
                return;
            }
            player = com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, resolved);
        }

        ILuaValue[] args;
        try {
            args = decodeArgs(data);
        } catch (RuntimeException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped RemoteEvent '" + channelName + "' with malformed payload: "
                            + e.getMessage());
            return;
        }

        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] OnClientFired firing RemoteEvent '" + channelName
                        + "' for player: " + (resolved != null ? resolved.getName() : "Unknown"));

        ILuaValue[] fireArgs = new ILuaValue[args.length + 1];
        fireArgs[0] = player;
        System.arraycopy(args, 0, fireArgs, 1, args.length);
        engine.callFunction(fireFn, appendThis(onServerEvent, fireArgs));
    }

    @Override
    public void OnServerFired(String channelName, String playerUuid, byte[] data) {
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
            args = decodeArgs(data);
        } catch (RuntimeException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped RemoteEvent '" + channelName + "' with malformed payload: "
                            + e.getMessage());
            return;
        }
        engine.callFunction(fireFn, appendThis(onClientEvent, args));
    }

    /** Empty payloads mean "no arguments"; anything else must decode cleanly. */
    private ILuaValue[] decodeArgs(@Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return NO_ARGS;
        }
        return LuaPayloadCodec.decodeArgs(engine, data);
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

    private ILuaValue[] appendThis(ILuaValue self, ILuaValue[] args) {
        ILuaValue[] result = new ILuaValue[args.length + 1];
        result[0] = self;
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }

    private enum RpcState {
        PENDING,
        DONE,
        ERROR
    }

    private static final class PendingRpc {
        private final long createdAtNanos = System.nanoTime();
        private volatile RpcState state = RpcState.PENDING;
        private volatile ILuaValue value;
        private volatile String error;
    }

    private static final class TokenBucket {
        private final double burst;
        private final double refillPerSecond;
        private double tokens;
        private long lastRefillNanos = System.nanoTime();

        private TokenBucket(double burst, double refillPerSecond) {
            this.burst = burst;
            this.refillPerSecond = refillPerSecond;
            this.tokens = burst;
        }

        private synchronized boolean tryConsume() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            lastRefillNanos = now;
            tokens = Math.min(burst, tokens + elapsedSeconds * refillPerSecond);
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
