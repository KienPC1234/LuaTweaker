package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.platform.config.LuaTweakerConfig;

/**
 * /luatweaker debug [on|off]
 *
 * Toggles the debug flag at runtime without requiring a server restart.
 * When no argument is given, prints the current debug state.
 */
public class DebugCommand implements ILuaTweakerCommand {

    @Override
    public String getName() { return "debug"; }

    @Override
    public String getDescription() { return "Toggle debug mode. Usage: debug [on|off]"; }

    @Override
    public int getPermissionLevel() { return 4; } // server owner only

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            boolean current = getDebug();
            sender.sendMessage("§6[LuaTweaker] Debug mode is currently §e" + (current ? "ON" : "OFF") + "§6.");
            return 1;
        }
        String flag = args[0].toLowerCase();
        if (flag.equals("on") || flag.equals("true") || flag.equals("1")) {
            setDebug(true);
            sender.sendSuccess("§aDebug mode §lENABLED§r§a. Reload scripts to apply.");
        } else if (flag.equals("off") || flag.equals("false") || flag.equals("0")) {
            setDebug(false);
            sender.sendSuccess("§eDebug mode §lDISABLED§r§e. Reload scripts to apply.");
        } else {
            sender.sendError("Unknown argument: " + args[0] + ". Use 'on' or 'off'.");
            return 0;
        }
        return 1;
    }

    private boolean getDebug() {
        try { return LuaTweakerConfig.DEBUG.get(); } catch (Exception e) { return false; }
    }

    private void setDebug(boolean value) {
        try { LuaTweakerConfig.DEBUG.set(value); } catch (Exception e) { com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Ignored exception: " + e.getMessage()); }
    }
}
