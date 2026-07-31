package com.luatweaker.api.network;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

@LuaDoc(description = "Rocket Network Service for managing client-server packet channels, RemoteEvents, and RemoteFunctions.")
public interface IRocketNetworkService {

    @LuaDoc(description = "Registers a remote event channel.", params = {"channelName: string"}, returnType = "any")
    ILuaTable GetOrCreateRemoteEvent(String channelName);

    @LuaDoc(description = "Registers a remote function channel for two-way request-response.", params = {"functionName: string"}, returnType = "any")
    ILuaTable GetOrCreateRemoteFunction(String functionName);

    @LuaDoc(description = "Fires a remote event from server to a specific player.", params = {"channelName: string", "playerUuid: string", "args: table"})
    void FireClient(String channelName, String playerUuid, ILuaValue[] args);

    @LuaDoc(description = "Fires a remote event from server to all players.", params = {"channelName: string", "args: table"})
    void FireAllClients(String channelName, ILuaValue[] args);

    @LuaDoc(description = "Fires a remote event from client to server.", params = {"channelName: string", "args: table"})
    void FireServer(String channelName, ILuaValue[] args);

    @LuaDoc(description = "Invokes a remote function on the server from the client and returns the result.", params = {"functionName: string", "args: table"}, returnType = "any")
    ILuaValue InvokeServer(String functionName, ILuaValue[] args);

    @LuaDoc(description = "Invokes a remote function on a client from the server and returns the result.", params = {"functionName: string", "playerUuid: string", "args: table"}, returnType = "any")
    ILuaValue InvokeClient(String functionName, String playerUuid, ILuaValue[] args);

    @LuaDoc(description = "Routes a client packet message received by server to active listeners.", params = {"channelName: string", "playerUuid: string", "args: table"})
    void OnClientFired(String channelName, String playerUuid, ILuaValue[] args);

    @LuaDoc(description = "Routes a server packet message received by client to active listeners.", params = {"channelName: string", "playerUuid: string", "args: table"})
    void OnServerFired(String channelName, String playerUuid, ILuaValue[] args);
}
