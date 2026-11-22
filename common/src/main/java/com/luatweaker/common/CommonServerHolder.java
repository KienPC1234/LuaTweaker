package com.luatweaker.common;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Platform-neutral holder of the active MinecraftServer.
 */
public final class CommonServerHolder {
    private static final AtomicReference<MinecraftServer> CURRENT_SERVER = new AtomicReference<>();

    private CommonServerHolder() {
    }

    public static void setServer(@Nullable MinecraftServer server) {
        CURRENT_SERVER.set(server);
    }

    @Nullable
    public static MinecraftServer getServer() {
        return CURRENT_SERVER.get();
    }

    @Nullable
    public static MinecraftServer get() {
        return CURRENT_SERVER.get();
    }
}
