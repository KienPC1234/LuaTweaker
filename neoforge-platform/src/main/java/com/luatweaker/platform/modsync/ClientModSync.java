package com.luatweaker.platform.modsync;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.ModSync;
import com.luatweaker.core.serverpacks.ServerBundleInfo;
import com.luatweaker.core.serverpacks.ServerBundleJson;
import com.luatweaker.core.serverpacks.ServerConsentStore;
import com.luatweaker.platform.client.download.ServerPackDownload;
import com.luatweaker.platform.client.download.ServerPackPromptScreen;
import com.luatweaker.update.ServerPackFetcher;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Client side of the LuaMod handshake: reports local required mods, surfaces
 * the server verdict as chat/log, and handles server bundle offers (prompt or
 * remembered-consent download into a per-bundle folder).
 */
public final class ClientModSync {

    private static final String LOG_PREFIX = "[ModSync] ";

    private ClientModSync() {
    }

    /** Builds the hello JSON the client sends on login. */
    public static @NotNull String buildHelloJson() {
        Map<String, String> required = ModSync.requiredMods(LuaModManager.getLoadedMods());
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"mods\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    /** Sends the hello to the server. Drops loudly instead of crashing the login flow. */
    public static void sendHello() {
        try {
            com.luatweaker.api.pal.Platform.getNetwork().sendPayloadPacketToServer(
                    com.luatweaker.platform.LuaTweakerMod.modHelloChannel(), buildHelloJson());
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_PREFIX + "Failed to send LuaMod hello: " + e.getMessage());
        }
    }

    /** Shows the server verdict on the client (chat + log). Called from the clientbound handler. */
    public static void handleVerdict(@NotNull String dataJson) {
        try {
            com.google.gson.JsonObject obj =
                    com.google.gson.JsonParser.parseString(dataJson).getAsJsonObject();
            if (obj.has("mods") && obj.get("mods").isJsonObject()) {
                sendHello();
                return;
            }
            boolean ok = obj.has("ok") && obj.get("ok").getAsBoolean();
            String message = obj.has("message") ? obj.get("message").getAsString() : "";
            if (ok) {
                LuaTweakerLog.get().info(LogStage.SYSTEM, LOG_PREFIX + "Server mods in sync.");
                return;
            }
            LuaTweakerLog.get().warn(LogStage.SYSTEM, LOG_PREFIX + message);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(message));
            }
            ServerBundleInfo offer = ServerBundleJson.parseVerdictOffer(dataJson);
            if (offer != null) {
                ModSyncBundles.storeOffer(offer);
                onBundleOffer(offer);
            }
        } catch (com.google.gson.JsonParseException | IllegalStateException
                | UnsupportedOperationException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    LOG_PREFIX + "Malformed sync verdict ignored: " + e.getMessage());
        }
    }

    /**
     * Starts a bundle download with chat feedback. Called from the prompt screen
     * and for remembered ALLOW consent.
     */
    public static void startDownload(@NotNull ServerBundleInfo offer, @NotNull Path serverDir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                    "Downloading server mod pack '" + offer.label() + "'..."));
        }
        ServerPackDownload.start(offer, serverDir, new ServerPackFetcher(), message -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal("[LuaTweaker] " + message));
                } else {
                    LuaTweakerLog.get().info(LogStage.SYSTEM, LOG_PREFIX + message);
                }
            });
        });
    }

    private static void onBundleOffer(@NotNull ServerBundleInfo offer) {
        Minecraft mc = Minecraft.getInstance();
        LuaTweakerLog.get().warn(LogStage.SYSTEM, LOG_PREFIX + "Server offers LuaMod pack '"
                + offer.label() + "' " + offer.version() + ": " + offer.url()
                + " (sha256 " + offer.sha256() + ")");
        if (mc.hasSingleplayerServer()) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    LOG_PREFIX + "Singleplayer: local mods already present, ignoring the offer.");
            return;
        }
        Path serverDir = serverDirFor(offer);
        ServerConsentStore.Decision decision = ServerConsentStore.read(serverDir);
        if (decision == ServerConsentStore.Decision.DENY) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    LOG_PREFIX + "Bundle declined before, skipping the download.");
            return;
        }
        if (decision == ServerConsentStore.Decision.ALLOW) {
            startDownload(offer, serverDir);
            return;
        }
        mc.execute(() -> {
            if (mc.player == null) {
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        LOG_PREFIX + "Player not in game yet, skipping the download prompt.");
                return;
            }
            mc.setScreen(new ServerPackPromptScreen(null, offer, serverDir));
        });
    }

    /** Per-bundle folder: {@code <gameDir>/luatweaker/servers/<contentId>/}. */
    static @NotNull Path serverDirFor(@NotNull ServerBundleInfo offer) {
        return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
                .resolve("luatweaker").resolve("servers").resolve(offer.bundleId());
    }

    private static @NotNull String escape(@NotNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
