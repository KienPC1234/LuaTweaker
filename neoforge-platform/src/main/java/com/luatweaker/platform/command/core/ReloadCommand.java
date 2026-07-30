package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.core.logger.AsyncFileLogger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * /luatweaker reload
 *
 * Re-executes all server Lua scripts immediately by calling Minecraft's
 * built-in resource manager reload, which triggers the LuaTweaker reload
 * listener registered in LuaTweakerMod.
 */
public class ReloadCommand implements ILuaTweakerCommand {

    @Override
    public String getName() { return "reload"; }

    @Override
    public String getDescription() { return "Reload all Lua scripts immediately."; }

    @Override
    public int getPermissionLevel() { return 2; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            sender.sendError("Server is not running.");
            return 0;
        }
        sender.sendSuccess("Reloading LuaTweaker scripts...");
        AsyncFileLogger.get().info("CMD", "Reload triggered by: " + sender.getName(), null);

        // Use Minecraft's /reload mechanism so all reload listeners fire
        server.reloadResources(server.getPackRepository().getSelectedIds())
              .thenRun(() -> sender.sendSuccess("§aReload complete."))
              .exceptionally(ex -> {
                  sender.sendError("Reload failed: " + ex.getMessage());
                  AsyncFileLogger.get().error("CMD", "Reload error: " + ex.getMessage(), null);
                  return null;
              });
        return 1;
    }
}
