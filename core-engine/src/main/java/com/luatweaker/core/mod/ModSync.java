package com.luatweaker.core.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure mod-sync comparison: which universal required LuaMods differ between server and client.
 * No Minecraft/NeoForge imports so unit tests run headless.
 */
public final class ModSync {
    private static final int MESSAGE_INITIAL_CAPACITY = 128;
    private static final int ELLIPSIS_LENGTH = 3;

    private ModSync() {
    }

    /** One required mod entry (id to version). */
    public record RequiredMod(@NotNull String id, @NotNull String version) {
    }

    /** Comparison result, pure data for kick/warn decisions and Lua reports. */
    public record Report(
            @NotNull List<RequiredMod> missingOnClient,
            @NotNull List<RequiredMod> missingOnServer,
            @NotNull List<String> versionMismatch,
            @NotNull List<String> versionDetail) {
        public boolean isOk() {
            return missingOnClient.isEmpty() && missingOnServer.isEmpty() && versionMismatch.isEmpty();
        }
    }

    /**
     * Collects the enforced set: universal environment + sync != optional.
     *
     * @param loaded live loaded mods (id to mod)
     * @return id to version map of required mods
     */
    public static @NotNull Map<String, String> requiredMods(@NotNull Map<String, LuaMod> loaded) {
        Map<String, String> out = new ConcurrentHashMap<>();
        for (Map.Entry<String, LuaMod> entry : loaded.entrySet()) {
            LuaModManifest manifest = entry.getValue().getManifest();
            if (isEnforced(manifest)) {
                out.put(manifest.id(), manifest.version());
            }
        }
        return Map.copyOf(out);
    }

    /** True when this manifest must match on both sides. */
    public static boolean isEnforced(@Nullable LuaModManifest manifest) {
        return manifest != null
                && "universal".equalsIgnoreCase(manifest.environment())
                && !"optional".equalsIgnoreCase(manifest.sync());
    }

    /**
     * Compares server required mods vs client reported mods.
     *
     * @param serverMods id to version on the server (already filtered to enforced)
     * @param clientMods id to version reported by the client (may contain extras)
     * @return report with missing and version-mismatch entries
     */
    public static @NotNull Report compare(@NotNull Map<String, String> serverMods,
                                          @Nullable Map<String, String> clientMods) {
        return ModSyncCompare.compare(serverMods, clientMods);
    }

    /** Parses a client hello JSON object into id to version. Returns empty on malformed input. */
    public static @NotNull Map<String, String> parseClientHello(@Nullable String json) {
        return ModSyncParsing.parseClientHello(json);
    }

    /** Builds a kick/warn message from a report, truncated to a chat-safe length. */
    public static @NotNull String buildMessage(@NotNull Report report, int maxChars) {
        if (report.isOk()) {
            return "LuaMods are in sync.";
        }
        StringBuilder sb = new StringBuilder(MESSAGE_INITIAL_CAPACITY);
        boolean first = true;
        for (RequiredMod mod : report.missingOnClient()) {
            first = appendSeparator(sb, first);
            sb.append("missing on client: ").append(mod.id()).append(" v").append(mod.version());
        }
        for (String line : report.versionDetail()) {
            first = appendSeparator(sb, first);
            sb.append("version mismatch ").append(line);
        }
        for (RequiredMod mod : report.missingOnServer()) {
            first = appendSeparator(sb, first);
            sb.append("server missing: ").append(mod.id()).append(" v").append(mod.version());
        }
        String msg = sb.toString();
        if (msg.length() > maxChars) {
            msg = msg.substring(0, Math.max(0, maxChars - ELLIPSIS_LENGTH)) + "...";
        }
        return msg;
    }

    private static boolean appendSeparator(StringBuilder sb, boolean first) {
        if (!first) {
            sb.append("; ");
        }
        return false;
    }
}
