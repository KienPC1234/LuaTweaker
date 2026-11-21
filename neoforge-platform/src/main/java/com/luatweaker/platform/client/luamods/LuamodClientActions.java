package com.luatweaker.platform.client.luamods;

import com.luatweaker.core.mod.LuaModManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-side actions behind the in-game LuaMod manager screens.
 * Disk state lives in {@link LuaModManager} (core); this class only adapts it
 * to the running client (game dir, chat feedback, reload trigger).
 */
public final class LuamodClientActions {
    private static final int SELF_ONLY_CASCADE_SIZE = 1;
    private static final int MAX_CONFIRM_LABEL_CHARS = 24;

    private LuamodClientActions() {}

    /** The luamods directory of the running game. */
    public static @NotNull File luamodsDir() {
        return new File(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile(), "luamods");
    }

    /** Fresh management snapshots for the screens; never triggers loading. */
    public static @NotNull List<com.luatweaker.core.mod.ModSummary> refreshSummaries() {
        return LuaModManager.listModSummaries(luamodsDir());
    }

    /**
     * Ids with a cached available update (declarative checker results).
     * Empty when the update service is absent or nothing is pending.
     */
    public static @NotNull java.util.Set<String> updateAvailableIds() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (com.luatweaker.update.UpdateStatus status : com.luatweaker.update.UpdateServiceImpl.getUpdates()) {
            if (status != null && status.modId() != null) {
                ids.add(status.modId());
            }
        }
        return ids;
    }

    /** One-line update hint for the detail screen, or {@code null} when up to date. */
    public static @Nullable String updateInfoFor(@NotNull String modId) {
        for (com.luatweaker.update.UpdateStatus status : com.luatweaker.update.UpdateServiceImpl.getUpdates()) {
            if (status != null && modId.equals(status.modId()) && status.latestVersion() != null) {
                String name = status.updateName();
                return "update: v" + status.currentVersion() + " -> v" + status.latestVersion()
                        + ((name == null || name.isBlank()) ? "" : " (" + name + ")");
            }
        }
        return null;
    }

    /** Case-insensitive id/name filter for the list search box. Pure for tests. */
    public static @NotNull List<com.luatweaker.core.mod.ModSummary> filterSummaries(
            @NotNull List<com.luatweaker.core.mod.ModSummary> all, @Nullable String query) {
        if (query == null || query.isBlank()) { return List.copyOf(all); }
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        List<com.luatweaker.core.mod.ModSummary> out = new ArrayList<>();
        for (com.luatweaker.core.mod.ModSummary summary : all) {
            if (summary.id().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || summary.name().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                out.add(summary);
            }
        }
        return out;
    }

    /**
     * Reloads LuaMods so toggles and config edits take effect.
     * Singleplayer drives the integrated server directly (same one-liner as
     * {@code /lt reload}); on a remote server it asks the server via chat
     * command, which enforces that side's permission level.
     */
    public static void requestReload() {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal("§e[LuaTweaker] Reloading LuaMods..."), false);
            }
            server.reloadResources(server.getPackRepository().getSelectedIds())
                    .thenRun(() -> server.execute(() -> {
                        if (minecraft.player != null) {
                            minecraft.player.displayClientMessage(
                                    Component.literal("§a[LuaTweaker] Reload complete. Reopen the manager to refresh."),
                                    false);
                        }
                    }))
                    .exceptionally(ex -> {
                        server.execute(() -> {
                            if (minecraft.player != null) {
                                minecraft.player.displayClientMessage(
                                        Component.literal("§c[LuaTweaker] Reload failed: " + ex.getMessage()), false);
                            }
                        });
                        return null;
                    });
            return;
        }
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("lt reload");
        }
    }

    /**
     * Reads the tail of a mod's dedicated log file.
     *
     * @param gameDir the game directory (FMLPaths GAMEDIR in production)
     * @param maxLines maximum lines returned (newest last)
     * @return log lines, or a single explanatory line when there is no log yet
     */
    public static @NotNull List<String> readLogTail(@NotNull File gameDir, @NotNull String modId, int maxLines) {
        if (!modId.matches("[A-Za-z0-9_.-]+")) {
            return List.of("(invalid mod id)");
        }
        File logFile = new File(new File(gameDir, "logs/luatweaker/mods"), modId + ".log");
        if (!logFile.isFile()) {
            return List.of("(no log yet for '" + modId + "' - it appears after the mod loads once)");
        }
        try {
            List<String> all = Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8);
            int from = Math.max(0, all.size() - Math.max(1, maxLines));
            return List.copyOf(all.subList(from, all.size()));
        } catch (IOException | SecurityException e) {
            return List.of("(failed to read log: " + e.getMessage() + ")");
        }
    }

    /**
     * Two-step toggle label for the detail screen: the first press arms the
     * cascade warning, the second executes. Long dependent lists collapse to a
     * count so the narrow button never overflows. Pure for tests.
     *
     * @param enabled current switch state
     * @param cascade full disable closure when armed, empty on the first press
     */
    public static @NotNull String toggleConfirmText(boolean enabled, @NotNull List<String> cascade) {
        if (!enabled) {
            return "Turn ON";
        }
        if (cascade.size() <= SELF_ONLY_CASCADE_SIZE) {
            return "Turn OFF";
        }
        List<String> others = new ArrayList<>(cascade.subList(1, cascade.size()));
        String joined = String.join(", ", others);
        if (joined.length() > MAX_CONFIRM_LABEL_CHARS) {
            return "Confirm OFF (" + others.size() + " mods)";
        }
        return "Confirm OFF (" + joined + ")";
    }
}
