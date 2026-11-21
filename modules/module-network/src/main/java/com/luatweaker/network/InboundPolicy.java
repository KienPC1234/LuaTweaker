package com.luatweaker.network;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-channel inbound switch. A mod can silence client-to-server traffic for its own channels
 * without unregistering them; both the RemoteEvent and RemoteFunction paths consult this policy.
 */
final class InboundPolicy {

    private final Set<String> blocked = ConcurrentHashMap.newKeySet();

    /** Live view used by the network report and diagnostics. */
    Set<String> names() {
        return blocked;
    }

    boolean isBlocked(@Nullable String channelName) {
        return channelName != null && blocked.contains(channelName);
    }

    void setEnabled(String channelName, boolean enabled) {
        if (enabled) {
            blocked.remove(channelName);
        } else {
            blocked.add(channelName);
        }
    }
}
