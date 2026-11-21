package com.luatweaker.network;

import com.luatweaker.api.pal.IPlatformNetwork;

import java.util.ArrayList;
import java.util.List;

/** Records every payload the network service hands to the transport. */
final class CapturingNetwork implements IPlatformNetwork {

    final List<Frame> payloads = new ArrayList<>();

    @Override
    public void sendPayloadBytes(String playerUuid, String channelName, byte[] data) {
        payloads.add(new Frame(playerUuid, channelName, data));
    }

    @Override
    public void broadcastPayloadBytes(String channelName, byte[] data) {
        payloads.add(new Frame("", channelName, data));
    }

    @Override
    public void sendPayloadBytesToServer(String channelName, byte[] data) {
        payloads.add(new Frame("", channelName, data));
    }

    Frame last() {
        return payloads.get(payloads.size() - 1);
    }
}
