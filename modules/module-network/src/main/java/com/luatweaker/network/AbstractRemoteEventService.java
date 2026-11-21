package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RemoteEvent half of {@link IRocketNetworkService}. Owns the shared channel maps, the
 * engine reference and the flood limiter so {@link NetworkServiceImpl} is left with the
 * registry/RPC surface only.
 */
abstract class AbstractRemoteEventService implements IRocketNetworkService {

    final Map<String, ILuaTable> remoteEvents = new ConcurrentHashMap<>();
    final Map<String, ILuaTable> remoteFunctions = new ConcurrentHashMap<>();
    final RateLimiter rateLimiter = new RateLimiter();
    final RemoteRegistry registry;
    private final RemoteEventDispatcher eventDispatcher;
    private final RemoteEventSender eventSender;

    AbstractRemoteEventService(ILuaEngine engine) {
        this.registry = new RemoteRegistry(engine, this, remoteEvents, remoteFunctions);
        this.eventDispatcher = new RemoteEventDispatcher(engine, remoteEvents, rateLimiter,
                NetworkServiceImpl.EVENT_BURST, NetworkServiceImpl.EVENT_REFILL_PER_SECOND,
                NetworkServiceImpl.EVENT_CHANNEL_BURST, NetworkServiceImpl.EVENT_CHANNEL_REFILL_PER_SECOND,
                registry.inboundPolicy());
        this.eventSender = new RemoteEventSender(rateLimiter, NetworkServiceImpl.EVENT_BURST,
                NetworkServiceImpl.EVENT_REFILL_PER_SECOND,
                NetworkServiceImpl.EVENT_CHANNEL_BURST, NetworkServiceImpl.EVENT_CHANNEL_REFILL_PER_SECOND,
                NetworkServiceImpl.LOCAL_CLIENT_BUDGET_KEY, remoteEvents::containsKey);
    }

    @Override
    public void FireClient(String channelName, String playerUuid, ILuaValue... args) {
        eventSender.fireClient(channelName, playerUuid, args);
    }

    @Override
    public void FireAllClients(String channelName, ILuaValue... args) {
        eventSender.fireAllClients(channelName, args);
    }

    @Override
    public void FireClients(String channelName, java.util.@NotNull List<String> playerUuids,
                            ILuaValue... args) {
        eventSender.fireClients(channelName, playerUuids, args);
    }

    @Override
    public void FireAllClientsExcept(String channelName, String excludedPlayerUuid, ILuaValue... args) {
        eventSender.fireAllClientsExcept(channelName, excludedPlayerUuid, args);
    }

    @Override
    public void FireServer(String channelName, ILuaValue... args) {
        eventSender.fireServer(channelName, args);
    }

    @Override
    public void OnClientFired(String channelName, String playerUuid, byte[] data) {
        eventDispatcher.onClientFired(channelName, playerUuid, data);
    }

    @Override
    public void OnServerFired(String channelName, String playerUuid, byte[] data) {
        eventDispatcher.onServerFired(channelName, playerUuid, data);
    }
}
