package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks outstanding RemoteFunction request/response round trips in this VM:
 * registration, polling, cancellation and result delivery with expiry.
 */
final class PendingRpcTracker {

    /** Upper bound on outstanding request/response round trips in this VM. */
    private static final int MAX_PENDING_RPC = 256;
    /**
     * Upper bound on outstanding requests attributed to one owner (one player on
     * the server, the local client on a client VM). A hung or hostile client
     * can only exhaust its own slice, never the shared pool.
     */
    private static final int MAX_PENDING_PER_OWNER = 16;
    /** A request nobody polls (dead coroutine, reload) is dropped after this. */
    private static final long PENDING_RPC_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);
    /** Owner key for requests started by this VM itself (client invoking the server). */
    private static final String LOCAL_OWNER = "local";
    private static final String STATUS_KEY = "Status";

    private final ILuaEngine engine;
    private final Map<String, PendingRpc> pendingRpcs = new ConcurrentHashMap<>();

    PendingRpcTracker(ILuaEngine engine) {
        this.engine = engine;
    }

    String registerRequest() {
        return registerRequest(LOCAL_OWNER);
    }

    String registerRequest(@Nullable String ownerKey) {
        String owner = ownerKey == null || ownerKey.isBlank() ? LOCAL_OWNER : ownerKey;
        pruneExpiredRequests();
        if (countByOwner(owner) >= MAX_PENDING_PER_OWNER) {
            throw new IllegalStateException("Too many unanswered RemoteFunction requests for this caller ("
                    + MAX_PENDING_PER_OWNER + "); responses were never polled. Use RpcCancel on timeout.");
        }
        if (pendingRpcs.size() >= MAX_PENDING_RPC) {
            throw new IllegalStateException("Too many unanswered RemoteFunction requests ("
                    + MAX_PENDING_RPC + "); responses were never polled. Use RpcCancel on timeout.");
        }
        String requestId = UUID.randomUUID().toString();
        pendingRpcs.put(requestId, new PendingRpc(owner));
        return requestId;
    }

    private int countByOwner(String owner) {
        int count = 0;
        for (PendingRpc pending : pendingRpcs.values()) {
            if (pending.state.get() == RpcState.PENDING && owner.equals(pending.owner)) {
                count++;
            }
        }
        return count;
    }

    void removeRequest(String requestId) {
        pendingRpcs.remove(requestId);
    }

    ILuaTable poll(String requestId) {
        ILuaTable result = engine.createTable();
        PendingRpc pending = requestId == null ? null : pendingRpcs.get(requestId);
        if (pending == null) {
            return pollError(result, "RemoteFunction request is unknown or already completed");
        }
        if (pending.state.get() == RpcState.PENDING
                && System.nanoTime() - pending.createdAtNanos > PENDING_RPC_TTL_NANOS) {
            pendingRpcs.remove(requestId);
            return pollError(result, "RemoteFunction request expired before a response arrived");
        }
        return pollState(result, requestId, pending);
    }

    private ILuaTable pollError(ILuaTable result, String message) {
        result.rawset(STATUS_KEY, engine.wrapString("error"));
        result.rawset("Error", engine.wrapString(message));
        return result;
    }

    private ILuaTable pollState(ILuaTable result, String requestId, PendingRpc pending) {
        switch (pending.state.get()) {
            case PENDING:
                result.rawset(STATUS_KEY, engine.wrapString("pending"));
                break;
            case DONE:
                // One-shot: the Lua wrapper consumes the value exactly once.
                pendingRpcs.remove(requestId);
                result.rawset(STATUS_KEY, engine.wrapString("done"));
                ILuaValue value = pending.value.get();
                result.rawset("Value", value != null ? value : engine.nilValue());
                break;
            case ERROR:
                pendingRpcs.remove(requestId);
                result.rawset(STATUS_KEY, engine.wrapString("error"));
                String error = pending.error.get();
                result.rawset("Error", engine.wrapString(
                        error != null ? error : "RemoteFunction call failed"));
                break;
        }
        return result;
    }

    void cancel(String requestId) {
        if (requestId != null) {
            pendingRpcs.remove(requestId);
        }
    }

    void complete(String requestId, byte[] payload, String error) {
        PendingRpc pending = requestId == null ? null : pendingRpcs.get(requestId);
        if (pending == null) {
            // Late answer for a timed-out/cancelled request, or a spoofed id.
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[Network] Ignored RemoteFunction response for unknown request " + requestId);
            return;
        }
        if (pending.state.get() != RpcState.PENDING) {
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[Network] Ignored duplicate RemoteFunction response " + requestId);
            return;
        }
        if (error != null && !error.isEmpty()) {
            pending.error.set(error);
            pending.state.set(RpcState.ERROR);
            return;
        }
        try {
            ILuaValue[] values = LuaPayloadCodec.decodeArgsOrEmpty(engine, payload);
            pending.value.set(values.length > 0 ? values[0] : engine.nilValue());
            pending.state.set(RpcState.DONE);
        } catch (IllegalArgumentException e) {
            pending.error.set("Malformed RemoteFunction response payload: " + e.getMessage());
            pending.state.set(RpcState.ERROR);
        }
    }

    private void pruneExpiredRequests() {
        long now = System.nanoTime();
        pendingRpcs.entrySet().removeIf(entry ->
                entry.getValue().state.get() == RpcState.PENDING
                        && now - entry.getValue().createdAtNanos > PENDING_RPC_TTL_NANOS);
    }

    private enum RpcState {
        PENDING,
        DONE,
        ERROR
    }

    private static final class PendingRpc {
        private final long createdAtNanos = System.nanoTime();
        private final AtomicReference<RpcState> state = new AtomicReference<>(RpcState.PENDING);
        private final AtomicReference<ILuaValue> value = new AtomicReference<>();
        private final AtomicReference<String> error = new AtomicReference<>();
        private final String owner;

        private PendingRpc(String owner) {
            this.owner = owner;
        }
    }
}
