package com.luatweaker.api.network;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

@LuaDoc(description = "Rocket Network Service for managing client-server packet channels, RemoteEvents, and RemoteFunctions.")
public interface IRocketNetworkService {

    @LuaDoc(description = "Registers a remote event channel.", params = {LuaDocFragments.Network.CHANNEL_NAME}, returnType = "any")
    ILuaTable GetOrCreateRemoteEvent(String channelName);

    @LuaDoc(description = "Registers a remote function channel for two-way request-response.", params = {"functionName: string"}, returnType = "any")
    ILuaTable GetOrCreateRemoteFunction(String functionName);

    @LuaDoc(description = "Fires a remote event from server to a specific player.", params = {LuaDocFragments.Network.CHANNEL_NAME, LuaDocFragments.Network.PLAYER_UUID, LuaDocFragments.Network.ARGS_TABLE})
    void FireClient(String channelName, String playerUuid, ILuaValue... args);

    @LuaDoc(description = "Fires a remote event from server to all players.", params = {LuaDocFragments.Network.CHANNEL_NAME, LuaDocFragments.Network.ARGS_TABLE})
    void FireAllClients(String channelName, ILuaValue... args);

    @LuaDoc(description = "Fires a remote event from client to server.", params = {LuaDocFragments.Network.CHANNEL_NAME, LuaDocFragments.Network.ARGS_TABLE})
    void FireServer(String channelName, ILuaValue... args);

    /**
     * Starts a RemoteFunction round trip to the server and returns its correlation id.
     * The caller polls {@link #RpcPoll(String)} until the answer arrives; the Lua
     * {@code RemoteFunction:InvokeServer} wrapper does that and yields the coroutine.
     *
     * @throws IllegalStateException when too many requests are still unanswered
     * @throws IllegalArgumentException when the function is not registered locally or the
     *                                  arguments/return value cannot be serialized
     */
    @LuaDoc(
        description = "Starts a RemoteFunction request to the server and returns the request id.",
        params = {"functionName: string", LuaDocFragments.Network.ARGS_TABLE},
        returnType = "string"
    )
    String RpcInvokeServer(String functionName, ILuaValue... args);

    /**
     * Starts a RemoteFunction round trip to one client and returns its correlation id.
     *
     * @see #RpcInvokeServer(String, ILuaValue[])
     */
    @LuaDoc(
        description = "Starts a RemoteFunction request to one client and returns the request id.",
        params = {"functionName: string", LuaDocFragments.Network.PLAYER_UUID, LuaDocFragments.Network.ARGS_TABLE},
        returnType = "string"
    )
    String RpcInvokeClient(String functionName, String playerUuid, ILuaValue... args);

    /**
     * Pops the state of a pending request.
     *
     * @return a table with {@code Status} = {@code "pending" | "done" | "error"}; when
     *         {@code "done"} it carries {@code Value}, when {@code "error"} it carries
     *         {@code Error}
     */
    @LuaDoc(
        description = "Pops the state of a pending RemoteFunction request.",
        params = {"requestId: string"},
        returnType = "table"
    )
    ILuaTable RpcPoll(String requestId);

    /** Drops a pending request (called by the Lua wrapper on timeout). */
    @LuaDoc(
        description = "Drops a pending RemoteFunction request.",
        params = {"requestId: string"}
    )
    void RpcCancel(String requestId);

    /**
     * Handles an incoming RemoteFunction invoke packet: runs {@code OnServerInvoke} (server
     * side, receives the calling player as first argument) or {@code OnClientInvoke} (client
     * side) and answers with the return value or a serialized failure.
     *
     * @param playerUuid caller uuid on the server; empty on the client
     * @param args       binary Lua argument payload ({@code LuaPayloadCodec})
     */
    void OnRpcInvoke(String requestId, String functionName, String playerUuid, byte[] args);

    /**
     * Handles an incoming RemoteFunction result packet and hands it to the waiting caller.
     *
     * @param payload binary single-element result payload; empty when {@code error} is set
     * @param error   empty on success, otherwise the failure message
     */
    void OnRpcResult(String requestId, byte[] payload, String error);

    @LuaDoc(description = "Routes a client packet message received by server to active listeners.",
            params = {LuaDocFragments.Network.CHANNEL_NAME, LuaDocFragments.Network.PLAYER_UUID, "data: bytes"})
    void OnClientFired(String channelName, String playerUuid, byte[] data);

    @LuaDoc(description = "Routes a server packet message received by client to active listeners.",
            params = {LuaDocFragments.Network.CHANNEL_NAME, LuaDocFragments.Network.PLAYER_UUID, "data: bytes"})
    void OnServerFired(String channelName, String playerUuid, byte[] data);
}
