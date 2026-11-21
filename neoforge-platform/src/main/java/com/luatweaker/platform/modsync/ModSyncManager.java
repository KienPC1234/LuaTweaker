package com.luatweaker.platform.modsync;

import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.ModSync;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side mod-sync state: client hello reports plus enforcement mode.
 * Pure Java state, no Minecraft imports except via callers, so logic is testable.
 */
public final class ModSyncManager {

    public static final String MODE_KICK = "kick";
    public static final String MODE_WARN = "warn";

    private static final Map<String, Map<String, String>> CLIENT_MODS = new ConcurrentHashMap<>();
    private static final Map<String, Long> JOIN_MILLIS = new ConcurrentHashMap<>();
    private static final AtomicReference<String> ENFORCEMENT_MODE = new AtomicReference<>(MODE_KICK);

    private ModSyncManager() {
    }

    public static void setEnforcementMode(String mode) {
        if (MODE_WARN.equalsIgnoreCase(mode)) {
            ENFORCEMENT_MODE.set(MODE_WARN);
            return;
        }
        ENFORCEMENT_MODE.set(MODE_KICK);
    }

    public static @NotNull String getEnforcementMode() {
        return ENFORCEMENT_MODE.get();
    }

    public static void onPlayerJoin(@NotNull String playerUuid) {
        JOIN_MILLIS.put(playerUuid, System.currentTimeMillis());
    }

    public static void onPlayerLeave(@NotNull String playerUuid) {
        CLIENT_MODS.remove(playerUuid);
        JOIN_MILLIS.remove(playerUuid);
    }

    public static void storeHello(@NotNull String playerUuid, @NotNull Map<String, String> mods) {
        Map<String, String> copy = new ConcurrentHashMap<>(mods);
        CLIENT_MODS.put(playerUuid, Collections.unmodifiableMap(copy));
        JOIN_MILLIS.remove(playerUuid);
    }

    public static @NotNull Map<String, String> clientMods(@NotNull String playerUuid) {
        Map<String, String> mods = CLIENT_MODS.get(playerUuid);
        return mods == null ? Map.of() : mods;
    }

    public static @NotNull Map<String, String> serverRequiredMods() {
        return ModSync.requiredMods(LuaModManager.getLoadedMods());
    }

    public static @NotNull ModSync.Report checkPlayer(@NotNull String playerUuid) {
        return ModSync.compare(serverRequiredMods(), clientMods(playerUuid));
    }

    public static boolean shouldKick(@NotNull ModSync.Report report) {
        return !report.isOk() && MODE_KICK.equalsIgnoreCase(ENFORCEMENT_MODE.get());
    }

    /** Players still waiting for a hello beyond the timeout. */
    public static @NotNull Map<String, Long> pendingJoins(long timeoutMillis) {
        long now = System.currentTimeMillis();
        Map<String, Long> out = new ConcurrentHashMap<>();
        for (Map.Entry<String, Long> entry : JOIN_MILLIS.entrySet()) {
            if (now - entry.getValue() >= timeoutMillis) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }
}
