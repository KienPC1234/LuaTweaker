package com.luatweaker.api.pal;

import java.nio.charset.StandardCharsets;

/**
 * Transport bridge for LuaTweaker's client/server messaging.
 *
 * <p>The interface is deliberately dumb: it moves <em>bytes</em> on a named channel. It does
 * not know about RemoteEvents, RemoteFunctions, JSON or binary encoding, so the whole
 * protocol lives in {@code module-network} where it can be unit tested without Minecraft.
 * The platform is responsible for splitting oversized messages into frames and reassembling
 * them on the receiving side.</p>
 *
 * <p>Delivery is best-effort and never blocks: a missing player or server is logged and the
 * message is dropped.</p>
 *
 * <p><b>Protocol 1.1.0 breaking change:</b> the three payload methods used to take a JSON
 * {@code String}. They now take {@code byte[]}; the {@code String} overloads below are
 * UTF-8 convenience wrappers for the client control channels. A transport that still
 * implements the old signatures is rejected by the NeoForge handshake (payload version).</p>
 */
public interface IPlatformNetwork {

    /** Sends one message to a specific player (server side) or to the own connection. */
    void sendPayloadBytes(String playerUuid, String channelName, byte[] data);

    /** Sends one message to every connected player (server side). */
    void broadcastPayloadBytes(String channelName, byte[] data);

    /**
     * Sends one message to a subset of players (server side). The payload is
     * encoded and split once, then fanned out, so targeting N players costs
     * one encode instead of N. Default loops over {@link #sendPayloadBytes};
     * transports override for single-split fan-out.
     */
    default void sendPayloadBytesToPlayers(java.util.List<String> playerUuids,
                                           String channelName, byte[] data) {
        if (playerUuids == null || playerUuids.isEmpty()) {
            return;
        }
        for (String uuid : playerUuids) {
            if (uuid != null && !uuid.isBlank()) {
                sendPayloadBytes(uuid, channelName, data);
            }
        }
    }

    /** Sends one message to the server (client side). */
    void sendPayloadBytesToServer(String channelName, byte[] data);

    /**
     * UTF-8 convenience for the small {@code LuaTweaker:} control channels whose payload is
     * a JSON object. Lua traffic must use the byte methods so it is not re-encoded.
     */
    default void sendPayloadPacket(String playerUuid, String channelName, String dataJson) {
        sendPayloadBytes(playerUuid, channelName, dataJson.getBytes(StandardCharsets.UTF_8));
    }

    /** @see #sendPayloadPacket(String, String, String) */
    default void broadcastPayloadPacket(String channelName, String dataJson) {
        broadcastPayloadBytes(channelName, dataJson.getBytes(StandardCharsets.UTF_8));
    }

    /** @see #sendPayloadPacket(String, String, String) */
    default void sendPayloadPacketToServer(String channelName, String dataJson) {
        sendPayloadBytesToServer(channelName, dataJson.getBytes(StandardCharsets.UTF_8));
    }
}
