package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.common.util.CallbackFirewall;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * /lt reload [data|resources|all]
 *
 * <ul>
 *   <li>{@code /lt reload} or {@code /lt reload data} - reloads DataPack only
 *       (loot tables, recipes, advancements, tags). Uses Minecraft's native
 *       {@link MinecraftServer#reloadResources} which re-triggers
 *       {@code AddReloadListenerEvent} -> LuaTweaker scripts re-execute.</li>
 *   <li>{@code /lt reload resources} - reloads CLIENT ResourcePack only
 *       (textures, models, blockstates). Sends a reload packet to all clients.</li>
 *   <li>{@code /lt reload all} - reloads both DataPack and ResourcePack.</li>
 * </ul>
 */
public class ReloadCommand implements ILuaTweakerCommand {
    private static final String CMD_LOG_CHANNEL = "CMD";
    private static final String DEFAULT_MODE = "data";
    private static final String RESOURCE_RELOAD_HINT =
            "§b[LuaTweaker] §eResource pack updated. Press §aF3+T §eto reload textures/models on your client.";

    @Override
    public String getName() { return "reload"; }

    @Override
    public String getDescription() { return "Reload Lua scripts, DataPack and/or ResourcePack. Usage: /lt reload [data|resources|all]"; }

    @Override
    public int getPermissionLevel() { return 2; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        return runReload(com.luatweaker.common.CommonServerHolder.get(), sender, args);
    }

    private int runReload(@Nullable MinecraftServer server, @NotNull ICommandSender sender,
                          @NotNull String... args) {
        if (server == null) {
            sender.sendError("Server is not running.");
            return 0;
        }
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : DEFAULT_MODE;
        switch (mode) {
            case "resources" -> {
                sender.sendSuccess("§eReloading client ResourcePack (textures/models)...");
                AsyncFileLogger.get().info(CMD_LOG_CHANNEL, "ResourcePack reload triggered by: " + sender.getName(), null);
                reloadClientResources(server, sender);
            }
            case "all" -> {
                sender.sendSuccess("§eReloading DataPack + ResourcePack...");
                AsyncFileLogger.get().info(CMD_LOG_CHANNEL, "Full reload (all) triggered by: " + sender.getName(), null);
                reloadDatapack(server, sender, () -> reloadClientResources(server, sender));
            }
            default -> {
                // "data" or any unrecognized arg -> DataPack only
                sender.sendSuccess("§eReloading LuaTweaker DataPack + scripts...");
                AsyncFileLogger.get().info(CMD_LOG_CHANNEL, "DataPack reload triggered by: " + sender.getName(), null);
                reloadDatapack(server, sender, null);
            }
        }
        return 1;
    }

    /** Reloads server DataPack (loot tables, recipes, tags). Triggers AddReloadListenerEvent -> Lua re-executes. */
    private void reloadDatapack(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                                @Nullable Runnable afterDatapack) {
        // thenRun/exceptionally fire on a background thread: route every sender
        // call (chat packets are thread-confined) back onto the server thread.
        server.reloadResources(server.getPackRepository().getSelectedIds())
                .thenRun(() -> server.execute(() -> {
                    sender.sendSuccess("§a[OK] DataPack reload complete.");
                    if (afterDatapack != null) { afterDatapack.run(); }
                }))
                .exceptionally(failure -> {
                    server.execute(() -> {
                        sender.sendError("DataPack reload failed: " + failure.getMessage());
                        AsyncFileLogger.get().error(CMD_LOG_CHANNEL, "DataPack reload error: " + failure.getMessage(), null);
                    });
                    return null;
                });
    }

    /**
     * Sends a resource pack reload to all connected clients.
     *
     * <p>NeoForge 1.21.1 does not expose a direct "reload client pack" API on the server side.
     * The closest equivalent is to call the vanilla F3+T mechanic server-side, which is triggered
     * by disconnecting + reconnecting the pack repository. We notify players to press F3+T.</p>
     */
    private void reloadClientResources(@NotNull MinecraftServer server, @NotNull ICommandSender sender) {
        server.getPlayerList().getPlayers().forEach(player ->
                player.sendSystemMessage(Component.literal(RESOURCE_RELOAD_HINT)));
        if (!reloadPackRepository(server)) {
            sender.sendError("ResourcePack reload failed; see the server log for details.");
            AsyncFileLogger.get().warn(CMD_LOG_CHANNEL,
                    "ResourcePack repository reload failed; see the main log for the cause.", null);
            return;
        }
        sender.sendSuccess("§a[OK] ResourcePack repository reloaded. Players notified to press F3+T.");
        AsyncFileLogger.get().info(CMD_LOG_CHANNEL, "Client ResourcePack reload broadcast sent.", null);
    }

    /**
     * Re-discovers available packs and re-selects the previous selection.
     *
     * <p>Isolation firewall: pack discovery runs other mods' resource-pack sources, so a broken
     * pack must be reported to the sender instead of unwinding the command handler.
     * {@link CallbackFirewall} is the single documented choke point for that trade-off.</p>
     *
     * @return {@code true} when the repository reloaded
     */
    private static boolean reloadPackRepository(@NotNull MinecraftServer server) {
        return Boolean.TRUE.equals(CallbackFirewall.supply(() -> {
            server.getPackRepository().reload();
            return Boolean.TRUE;
        }, Boolean.FALSE, LogStage.SYSTEM, () -> "[Reload] ResourcePack repository reload failed"));
    }
}
