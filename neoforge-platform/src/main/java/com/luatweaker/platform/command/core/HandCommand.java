package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;

/**
 * /luatweaker hand
 *
 * Prints the registry ID of the item the player is currently holding.
 * Useful when writing Lua recipes — e.g. "minecraft:diamond_sword".
 */
public class HandCommand implements ILuaTweakerCommand {

    @Override
    public String getName() { return "hand"; }

    @Override
    public String getDescription() { return "Print the registry ID of the item you are holding."; }

    @Override
    public boolean isConsoleAllowed() { return false; } // console has no hand

    @Override
    public int execute(ICommandSender sender, String[] args) {
        String id = sender.getHeldItemId();
        if (id == null || id.isEmpty()) {
            sender.sendError("You are not holding any item.");
            return 0;
        }
        sender.sendSuccess("Held item: §e" + id);
        return 1;
    }
}
