package com.luatweaker.common.modsync;

import com.google.gson.JsonObject;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.config.LuaTweakerConfig;
import com.luatweaker.core.mod.ModSync;
import com.luatweaker.core.serverpacks.ServerBundleInfo;
import com.luatweaker.core.serverpacks.ServerBundleJson;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Server-side ModSync handshake coordinator: configures advertised bundles,
 * challenges joining players, processes client hello reports, and enforces
 * warn/kick verdicts.
 */
public final class ServerModSync {

    private static final int MAX_VERDICT_MESSAGE_CHARS = 240;

    private ServerModSync() {
    }

    public static void syncConfig() {
        ModSyncManager.setEnforcementMode(LuaTweakerConfig.getModSyncMode());
        String url = LuaTweakerConfig.getServerModBundleUrl();
        if (url == null || url.isBlank()) {
            ModSyncBundles.setAdvertised(null);
            return;
        }
        JsonObject bundleObj = new JsonObject();
        bundleObj.addProperty("url", url);
        bundleObj.addProperty("sha256", LuaTweakerConfig.getServerModBundleSha256());
        bundleObj.addProperty("version", LuaTweakerConfig.getServerModBundleVersion());
        bundleObj.addProperty("name", LuaTweakerConfig.getServerModBundleName());
        bundleObj.addProperty("filename", LuaTweakerConfig.getServerModBundleFile());
        ModSyncBundles.setAdvertised(ServerBundleJson.parseBundle(bundleObj.toString()));
    }

    public static void onPlayerJoin(@NotNull ServerPlayer player) {
        String uuid = player.getUUID().toString();
        ModSyncManager.onPlayerJoin(uuid);
        try {
            Platform.getNetwork().sendPayloadPacket(
                    uuid, LuaTweakerCommon.modSyncChannel(), buildChallengeJson());
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ModSync] Skipped join challenge for " + uuid + ": " + e.getMessage());
        }
    }

    public static void onPlayerQuit(@NotNull ServerPlayer player) {
        ModSyncManager.onPlayerLeave(player.getUUID().toString());
    }

    public static void handleClientHello(@Nullable Player player, @NotNull String helloJson) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String uuid = serverPlayer.getUUID().toString();
        ModSyncManager.storeHello(uuid, ModSync.parseClientHello(helloJson));
        ModSync.Report report = ModSyncManager.checkPlayer(uuid);
        String verdictJson = buildVerdictJson(report, ModSyncBundles.getAdvertised());

        try {
            Platform.getNetwork().sendPayloadPacket(uuid, LuaTweakerCommon.modSyncChannel(), verdictJson);
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ModSync] Failed to send verdict to " + uuid + ": " + e.getMessage());
        }

        MinecraftServer server = serverPlayer.getServer();
        boolean singleplayerOwner = server != null && server.isSingleplayerOwner(serverPlayer.getGameProfile());
        if (ModSyncManager.shouldKick(report) && !singleplayerOwner) {
            String reason = ModSync.buildMessage(report, MAX_VERDICT_MESSAGE_CHARS);
            serverPlayer.connection.disconnect(Component.literal("[LuaTweaker] " + reason));
        }
    }

    @NotNull
    public static String buildChallengeJson() {
        Map<String, String> required = ModSyncManager.serverRequiredMods();
        JsonObject mods = new JsonObject();
        for (Map.Entry<String, String> entry : required.entrySet()) {
            mods.addProperty(entry.getKey(), entry.getValue());
        }
        JsonObject root = new JsonObject();
        root.add("mods", mods);
        return root.toString();
    }

    @NotNull
    public static String buildVerdictJson(@NotNull ModSync.Report report,
                                          @Nullable ServerBundleInfo advertised) {
        String message = ModSync.buildMessage(report, MAX_VERDICT_MESSAGE_CHARS);
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"ok\":").append(report.isOk())
                .append(",\"message\":\"").append(escape(message)).append('"');
        if (!report.isOk() && advertised != null) {
            sb.append(",\"download\":").append(ServerBundleJson.toJson(advertised));
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
