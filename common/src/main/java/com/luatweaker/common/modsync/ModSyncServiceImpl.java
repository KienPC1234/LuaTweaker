package com.luatweaker.common.modsync;

import com.luatweaker.api.modsync.IModSyncService;
import com.luatweaker.core.mod.ModSync;
import com.luatweaker.core.serverpacks.ServerBundleInfo;
import com.luatweaker.core.serverpacks.ServerBundleJson;
import org.jetbrains.annotations.NotNull;

/** LuaBinder implementation of the low-level mod-sync primitives. */
public final class ModSyncServiceImpl implements IModSyncService {

    @Override
    public @NotNull String getServerModsJson() {
        return toJson(ModSyncManager.serverRequiredMods());
    }

    @Override
    public @NotNull String getClientModsJson(@NotNull String playerUuid) {
        if (playerUuid.isBlank()) {
            throw new IllegalArgumentException("ModSync:GetClientMods requires a player UUID");
        }
        return toJson(ModSyncManager.clientMods(playerUuid));
    }

    @Override
    public @NotNull String checkPlayer(@NotNull String playerUuid) {
        if (playerUuid.isBlank()) {
            throw new IllegalArgumentException("ModSync:CheckPlayer requires a player UUID");
        }
        ModSync.Report report = ModSyncManager.checkPlayer(playerUuid);
        StringBuilder sb = new StringBuilder(64);
        sb.append("{\"ok\":").append(report.isOk()).append(",\"missing\":[");
        appendMods(sb, report);
        sb.append("],\"mismatch\":[");
        boolean first = true;
        for (String id : report.versionMismatch()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(id)).append('"');
            first = false;
        }
        sb.append("],\"detail\":\"").append(escape(ModSync.buildMessage(report, 400))).append("\"}");
        return sb.toString();
    }

    @Override
    public @NotNull String getEnforcementMode() {
        return ModSyncManager.getEnforcementMode();
    }

    @Override
    public @NotNull String getBundleInfoJson() {
        ServerBundleInfo advertised = ModSyncBundles.getAdvertised();
        ServerBundleInfo offer = advertised != null ? advertised : ModSyncBundles.getLastOffer();
        return offer == null ? "" : ServerBundleJson.toJson(offer);
    }

    @Override
    public void setEnforcementMode(@NotNull String mode) {
        if (!ModSyncManager.MODE_KICK.equalsIgnoreCase(mode)
                && !ModSyncManager.MODE_WARN.equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("ModSync enforcement must be 'kick' or 'warn'");
        }
        ModSyncManager.setEnforcementMode(mode);
    }

    private static void appendMods(StringBuilder sb, ModSync.Report report) {
        boolean first = true;
        for (ModSync.RequiredMod mod : report.missingOnClient()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(mod.id())).append(':').append(escape(mod.version())).append('"');
            first = false;
        }
    }

    private static @NotNull String toJson(java.util.Map<String, String> mods) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : mods.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private static @NotNull String escape(@NotNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
