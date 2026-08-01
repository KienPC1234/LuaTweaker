package com.luatweaker.platform.network;

import com.luatweaker.api.pal.IPlatformNetwork;
import net.minecraft.server.MinecraftServer;

public class NeoForgeNetworkPlatform implements IPlatformNetwork {
    @Override
    public void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Network] [SERVER -> CLIENT] Sending packet '" + channelName + "' to player UUID: " + playerUuid
        );
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                java.util.UUID uuid = java.util.UUID.fromString(playerUuid);
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    com.luatweaker.platform.network.LuaTweakerPayload payload = new com.luatweaker.platform.network.LuaTweakerPayload(channelName, dataJson);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
                }
            } catch (Exception e) {
                com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.SYSTEM, "Failed sendPayloadPacket: " + e.getMessage());
            }
        }
    }

    @Override
    public void broadcastPayloadPacket(String channelName, String dataJson) {
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Network] [SERVER -> ALL CLIENTS] Broadcasting packet '" + channelName + "'"
        );
        com.luatweaker.platform.network.LuaTweakerPayload payload = new com.luatweaker.platform.network.LuaTweakerPayload(channelName, dataJson);
        net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(payload);
    }

    @Override
    public void sendPayloadPacketToServer(String channelName, String dataJson) {
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Network] [CLIENT -> SERVER] Sending packet '" + channelName + "' with payload: " + dataJson
        );
        com.luatweaker.platform.network.LuaTweakerPayload payload = new com.luatweaker.platform.network.LuaTweakerPayload(channelName, dataJson);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
    }
}
