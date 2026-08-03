package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;

import java.util.List;

/**
 * /luatweaker help
 *
 * Lists all registered LuaTweaker commands with their descriptions.
 * Automatically populated from the command registry — no manual updates needed.
 */
public class HelpCommand implements ILuaTweakerCommand {

    private final List<ILuaTweakerCommand> allCommands;

    public HelpCommand(List<ILuaTweakerCommand> allCommands) {
        this.allCommands = allCommands;
    }

    @Override
    public String getName() { return "help"; }

    @Override
    public String getDescription() { return "Show all available LuaTweaker commands."; }

    @Override
    public int getPermissionLevel() { return 0; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        sender.sendMessage("§6╔══ §eLuaTweaker Commands §6══╗");
        for (ILuaTweakerCommand cmd : allCommands) {
            sender.sendMessage("§6  /lt §e" + cmd.getName()
                    + " §7- " + cmd.getDescription()
                    + " §8(op:" + cmd.getPermissionLevel() + ")");
        }
        // Lua mod commands are top-level (no /lt prefix); resolved live so
        // /lt reload changes appear here too.
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("CommandServiceImpl");
        if (service instanceof com.luatweaker.command.CommandServiceImpl commandService) {
            for (com.luatweaker.command.CommandDefinition def : commandService.getSnapshot()) {
                sender.sendMessage("§6  /§e" + def.name()
                        + " §7- " + def.description()
                        + " §8(op:" + def.permissionLevel() + ", mod:" + def.modId() + ")");
            }
        }
        sender.sendMessage("§6╚══════════════════════════╝");
        return 1;
    }
}
