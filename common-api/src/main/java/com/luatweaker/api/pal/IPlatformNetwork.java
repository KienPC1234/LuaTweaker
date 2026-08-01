package com.luatweaker.api.pal;

public interface IPlatformNetwork {
    void sendPayloadPacket(String playerUuid, String channelName, String dataJson);
    void broadcastPayloadPacket(String channelName, String dataJson);
    void sendPayloadPacketToServer(String channelName, String dataJson);
}
