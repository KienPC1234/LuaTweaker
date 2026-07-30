package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /luatweaker list [folder]
 *
 * Lists all .lua scripts in a given sub-folder (default: server/).
 * Useful for quickly seeing which scripts are loaded.
 */
public class ListCommand implements ILuaTweakerCommand {

    private final File luaRoot;

    public ListCommand(File luaRoot) {
        this.luaRoot = luaRoot;
    }

    @Override
    public String getName() { return "list"; }

    @Override
    public String getDescription() { return "List all Lua scripts in a folder. Usage: list [server|client|startup]"; }

    @Override
    public int getPermissionLevel() { return 0; } // any player can list scripts

    @Override
    public int execute(ICommandSender sender, String[] args) {
        String folder = args.length > 0 ? args[0] : "server";
        File dir = new File(luaRoot, folder);

        if (!dir.exists() || !dir.isDirectory()) {
            sender.sendError("Unknown script folder: lua/" + folder);
            return 0;
        }

        File[] files = dir.listFiles((d, n) -> n.endsWith(".lua"));
        if (files == null || files.length == 0) {
            sender.sendMessage("§7[LuaTweaker] No scripts in lua/" + folder + "/");
            return 1;
        }

        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        sender.sendMessage("§6[LuaTweaker] Scripts in §elua/" + folder + "§6 (" + files.length + "):");
        for (File f : files) {
            sender.sendMessage("  §7- §f" + f.getName());
        }
        return 1;
    }
}
