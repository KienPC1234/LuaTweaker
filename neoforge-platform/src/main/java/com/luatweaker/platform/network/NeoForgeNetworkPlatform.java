package com.luatweaker.platform.network;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformNetwork;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

public class NeoForgeNetworkPlatform implements IPlatformNetwork {

    @Override
    public void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [SERVER -> CLIENT] Sending packet '" + channelName + "' to " + playerUuid);
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped server packet '" + channelName + "': no server running");
            return;
        }
        try {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(playerUuid));
            if (player == null) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Network] Dropped packet '" + channelName + "': player " + playerUuid + " is offline");
                return;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new LuaTweakerPayload(channelName, dataJson));
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed sendPayloadPacket: invalid player uuid '" + playerUuid + "': " + e.getMessage());
        }
    }

    @Override
    public void broadcastPayloadPacket(String channelName, String dataJson) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [SERVER -> ALL CLIENTS] Broadcasting packet '" + channelName + "'");
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(
                new LuaTweakerPayload(channelName, dataJson));
    }

    @Override
    public void sendPayloadPacketToServer(String channelName, String dataJson) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Network] [CLIENT -> SERVER] Sending packet '" + channelName + "'");
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new LuaTweakerPayload(channelName, dataJson));
    }

    @Override
    public void sendRpcPacket(String playerUuid, String requestId, String phase,
                              String functionName, String dataJson, String error) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped RemoteFunction " + phase + " '" + functionName + "': no server running");
            return;
        }
        net.minecraft.server.level.ServerPlayer player;
        try {
            player = server.getPlayerList().getPlayer(UUID.fromString(playerUuid));
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[Network] Dropped RemoteFunction " + phase + " '" + functionName
                            + "': invalid player uuid '" + playerUuid + "'");
            return;
        }
        if (player == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Network] Dropped RemoteFunction " + phase + " '" + functionName
                            + "': player " + playerUuid + " is offline");
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new LuaTweakerRpcPayload(requestId, phase, functionName, dataJson, error));
    }

    @Override
    public void sendRpcPacketToServer(String requestId, String phase,
                                      String functionName, String dataJson, String error) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new LuaTweakerRpcPayload(requestId, phase, functionName, dataJson, error));
    }
}
