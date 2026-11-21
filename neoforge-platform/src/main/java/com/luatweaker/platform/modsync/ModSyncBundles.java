package com.luatweaker.platform.modsync;

import com.luatweaker.core.serverpacks.ServerBundleInfo;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;

/**
 * Bundle offer state for the ModSync handshake, kept apart from the hello and
 * enforcement state in {@link ModSyncManager}: the advertised pack (server
 * config) and the last offer a client received (verdict) change for different
 * reasons and are read on different sides.
 *
 * <p>Pure static state, no Minecraft imports, so logic stays headless-testable.</p>
 */
public final class ModSyncBundles {

    private static final AtomicReference<ServerBundleInfo> ADVERTISED = new AtomicReference<>();
    private static final AtomicReference<ServerBundleInfo> LAST_OFFER = new AtomicReference<>();

    private ModSyncBundles() {
    }

    /**
     * Server-advertised bundle (from config), or {@code null} when unconfigured/invalid.
     * Read by the verdict builder and by Lua on the server side.
     */
    public static void setAdvertised(@Nullable ServerBundleInfo bundle) {
        ADVERTISED.set(bundle);
    }

    public static @Nullable ServerBundleInfo getAdvertised() {
        return ADVERTISED.get();
    }

    /**
     * Last bundle offer received on the client (from a verdict), or {@code null}.
     * Written by the client handshake, read by Lua on the client side.
     */
    public static void storeOffer(@Nullable ServerBundleInfo bundle) {
        LAST_OFFER.set(bundle);
    }

    public static @Nullable ServerBundleInfo getLastOffer() {
        return LAST_OFFER.get();
    }

    /** Clears advertised and received bundle state (tests and config reloads). */
    public static void clear() {
        ADVERTISED.set(null);
        LAST_OFFER.set(null);
    }
}
