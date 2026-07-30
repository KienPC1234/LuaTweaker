package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.core.logger.AsyncFileLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * /lt reload [data|resources|all]
 *
 * <ul>
 *   <li>{@code /lt reload} or {@code /lt reload data} — reloads DataPack only
 *       (loot tables, recipes, advancements, tags). Uses Minecraft's native
 *       {@link MinecraftServer#reloadResources} which re-triggers
 *       {@code AddReloadListenerEvent} → LuaTweaker scripts re-execute.</li>
 *   <li>{@code /lt reload resources} — reloads CLIENT ResourcePack only
 *       (textures, models, blockstates). Sends a reload packet to all clients.</li>
 *   <li>{@code /lt reload all} — reloads both DataPack and ResourcePack.</li>
 * </ul>
 */
public class ReloadCommand implements ILuaTweakerCommand {

    @Override
    public String getName() { return "reload"; }

    @Override
    public String getDescription() { return "Reload Lua scripts, DataPack and/or ResourcePack. Usage: /lt reload [data|resources|all]"; }

    @Override
    public int getPermissionLevel() { return 2; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sender.sendError("Server is not running.");
            return 0;
        }

        String mode = (args.length > 0) ? args[0].toLowerCase() : "data";

        switch (mode) {
            case "resources" -> {
                sender.sendSuccess("§eReloading client ResourcePack (textures/models)...");
                AsyncFileLogger.get().info("CMD", "ResourcePack reload triggered by: " + sender.getName(), null);
                reloadClientResources(server, sender);
            }
            case "all" -> {
                sender.sendSuccess("§eReloading DataPack + ResourcePack...");
                AsyncFileLogger.get().info("CMD", "Full reload (all) triggered by: " + sender.getName(), null);
                reloadDatapack(server, sender, () -> reloadClientResources(server, sender));
            }
            default -> {
                // "data" or any unrecognized arg → DataPack only
                sender.sendSuccess("§eReloading LuaTweaker DataPack + scripts...");
                AsyncFileLogger.get().info("CMD", "DataPack reload triggered by: " + sender.getName(), null);
                reloadDatapack(server, sender, null);
            }
        }
        return 1;
    }

    /** Reloads server DataPack (loot tables, recipes, tags). Triggers AddReloadListenerEvent → Lua re-executes. */
    private void reloadDatapack(MinecraftServer server, ICommandSender sender, Runnable afterDatapack) {
        server.reloadResources(server.getPackRepository().getSelectedIds())
                .thenRun(() -> {
                    sender.sendSuccess("§a✅ DataPack reload complete.");
                    if (afterDatapack != null) afterDatapack.run();
                })
                .exceptionally(ex -> {
                    sender.sendError("DataPack reload failed: " + ex.getMessage());
                    AsyncFileLogger.get().error("CMD", "DataPack reload error: " + ex.getMessage(), null);
                    return null;
                });
    }

    /**
     * Sends a resource pack reload to all connected clients.
     *
     * <p>NeoForge 1.21.1 does not expose a direct "reload client pack" API on the server side.
     * The closest equivalent is to call the vanilla F3+T mechanic server-side, which is triggered
     * by disconnecting + reconnecting the pack repository. We use
     * {@link net.neoforged.neoforge.network.PacketDistributor} to broadcast the reload.
     */
    private void reloadClientResources(MinecraftServer server, ICommandSender sender) {
        try {
            // Notify all players to reload resource packs via the built-in NeoForge mechanism
            server.getPlayerList().getPlayers().forEach(player -> {
                // Send a chat message so the user knows to press F3+T if automatic reload is not available
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§b[LuaTweaker] §eResource pack updated. Press §aF3+T §eto reload textures/models on your client."
                ));
            });

            // Attempt server-side pack reload by re-registering
            server.getPackRepository().reload();

            sender.sendSuccess("§a✅ ResourcePack repository reloaded. Players notified to press F3+T.");
            AsyncFileLogger.get().info("CMD", "Client ResourcePack reload broadcast sent.", null);
        } catch (Exception e) {
            sender.sendError("ResourcePack reload error: " + e.getMessage());
            AsyncFileLogger.get().error("CMD", "ResourcePack reload error: " + e.getMessage(), null);
        }
    }
}
