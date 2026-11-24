package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.core.vm.LuaEngineFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@code /luatweaker syntax [path]}
 *
 * <p>Checks Lua file(s) inside {@code luamods/} (or legacy {@code server/}) for
 * syntax errors without executing them.</p>
 */
public class SyntaxCommand implements ILuaTweakerCommand {

    private static final Set<String> PHASE_NAMES = Set.of("server", "client", "startup");

    private final File luaRoot;

    public SyntaxCommand(File luaRoot) {
        this.luaRoot = luaRoot;
    }

    @Override
    public String getName() {
        return "syntax";
    }

    @Override
    public String getDescription() {
        return "Check Lua file(s) for syntax errors. Usage: syntax [path|<mod_id>]";
    }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            File legacyServer = new File(luaRoot, "server");
            if (legacyServer.isDirectory()) {
                return checkDirectory(sender, legacyServer);
            }
            return checkDirectory(sender, luaRoot);
        }
        File target = ListCommand.resolveInsideRoot(luaRoot, args[0]);
        if (target == null) {
            sender.sendError("Refusing path outside the lua/ folder: " + args[0]);
            return 0;
        }
        if (!target.exists() && PHASE_NAMES.contains(args[0])) {
            List<Path> phaseFiles = collectPhaseFiles(args[0]);
            if (!phaseFiles.isEmpty()) {
                return checkPaths(sender, luaRoot.toPath(), phaseFiles);
            }
        }
        if (!target.exists()) {
            sender.sendError("File not found: " + args[0]);
            return 0;
        }
        if (target.isDirectory()) {
            return checkDirectory(sender, target);
        }
        return checkFile(sender, luaRoot.toPath(), target.toPath());
    }

    private List<Path> collectPhaseFiles(String phase) {
        List<Path> files = new ArrayList<>();
        File[] modDirs = luaRoot.listFiles(File::isDirectory);
        if (modDirs == null) {
            return files;
        }
        for (File modDir : modDirs) {
            File phaseDir = modDir.toPath().resolve("src").resolve(phase).toFile();
            if (phaseDir.isDirectory()) {
                files.addAll(ListCommand.collectLuaFiles(phaseDir));
            }
        }
        return files;
    }

    private int checkDirectory(ICommandSender sender, File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            sender.sendError("Directory not found: " + dir.getPath());
            return 0;
        }
        List<Path> files = ListCommand.collectLuaFiles(dir);
        if (files.isEmpty()) {
            sender.sendMessage("§7No .lua files found in " + dir.getName() + "/");
            return 1;
        }
        return checkPaths(sender, dir.toPath(), files);
    }

    private int checkPaths(ICommandSender sender, Path baseDir, List<Path> files) {
        int errors = 0;
        for (Path file : files) {
            if (checkFile(sender, baseDir, file) == 0) {
                errors++;
            }
        }
        if (errors == 0) {
            sender.sendSuccess("All " + files.size() + " file(s) are syntax-clean.");
        } else {
            sender.sendError(errors + " file(s) have syntax errors.");
        }
        return errors == 0 ? 1 : 0;
    }

    private int checkFile(ICommandSender sender, Path baseDir, Path file) {
        String displayName = baseDir.relativize(file).toString().replace('\\', '/');
        if (displayName.isEmpty()) {
            displayName = file.getFileName().toString();
        }
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            String error = LuaEngineFactory.checkSyntax(displayName, source);
            if (error == null) {
                sender.sendSuccess("§a[OK] §f" + displayName);
                return 1;
            }
            sender.sendError("§c[FAIL] §f" + displayName + " §8-> §c" + error);
            return 0;
        } catch (IOException e) {
            sender.sendError("Cannot read file: " + displayName);
            return 0;
        }
    }
}
