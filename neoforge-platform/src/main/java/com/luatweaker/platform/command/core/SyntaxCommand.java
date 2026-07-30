package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.core.engine.LuaEngine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * /luatweaker syntax [file]
 *
 * Checks a Lua file (relative to the lua/ folder) for syntax errors without
 * executing it. Helpful during development.
 *
 * Usage examples:
 *   /luatweaker syntax server/myrecipe.lua
 *   /luatweaker syntax               → checks all files in lua/server/
 */
public class SyntaxCommand implements ILuaTweakerCommand {

    private final File luaRoot;

    public SyntaxCommand(File luaRoot) {
        this.luaRoot = luaRoot;
    }

    @Override
    public String getName() { return "syntax"; }

    @Override
    public String getDescription() { return "Check Lua file(s) for syntax errors. Usage: syntax [path]"; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            // Check all files in server/
            return checkDirectory(sender, new File(luaRoot, "server"));
        }
        File target = new File(luaRoot, args[0]);
        if (!target.exists()) {
            sender.sendError("File not found: lua/" + args[0]);
            return 0;
        }
        if (target.isDirectory()) {
            return checkDirectory(sender, target);
        }
        return checkFile(sender, target);
    }

    private int checkDirectory(ICommandSender sender, File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            sender.sendError("Directory not found: " + dir.getPath());
            return 0;
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".lua"));
        if (files == null || files.length == 0) {
            sender.sendMessage("§7No .lua files found in " + dir.getName() + "/");
            return 1;
        }
        int errors = 0;
        for (File f : files) {
            if (checkFile(sender, f) == 0) errors++;
        }
        if (errors == 0) {
            sender.sendSuccess("All " + files.length + " file(s) are syntax-clean.");
        } else {
            sender.sendError(errors + " file(s) have syntax errors.");
        }
        return errors == 0 ? 1 : 0;
    }

    private int checkFile(ICommandSender sender, File file) {
        try {
            String source = Files.readString(file.toPath());
            String error = LuaEngine.checkSyntax(file.getName(), source);
            if (error == null) {
                sender.sendSuccess("§a✔ §f" + file.getName());
                return 1;
            } else {
                sender.sendError("§c✘ §f" + file.getName() + " §8→ §c" + error);
                return 0;
            }
        } catch (IOException e) {
            sender.sendError("Cannot read file: " + file.getName());
            return 0;
        }
    }
}
