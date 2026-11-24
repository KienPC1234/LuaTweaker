package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.ModSummary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code /luatweaker list [folder|mod_id]}
 *
 * <p>Supports both the autonomous {@code luamods/<mod_id>/} layout and flat
 * script directories inside {@code luaRoot}.</p>
 */
public class ListCommand implements ILuaTweakerCommand {

    private static final Set<String> PHASE_NAMES = Set.of("server", "client", "startup");
    private static final String STUBS_DIR = ".luatweaker";

    private final File luaRoot;

    public ListCommand(File luaRoot) {
        this.luaRoot = luaRoot;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return "List loaded LuaMods or Lua scripts. Usage: list [server|client|startup|<mod_id>]";
    }

    @Override
    public int getPermissionLevel() {
        return 0;
    }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            return listDefault(sender);
        }
        String folder = args[0];
        File dir = resolveInsideRoot(luaRoot, folder);
        if (dir == null) {
            sender.sendError("Unknown script folder: lua/" + folder);
            return 0;
        }
        if ((!dir.exists() || !dir.isDirectory()) && PHASE_NAMES.contains(folder)) {
            List<String> phaseScripts = collectPhaseScriptsAcrossMods(folder);
            if (!phaseScripts.isEmpty()) {
                printScriptPaths(sender, "luamods/*/src/" + folder, phaseScripts);
                return 1;
            }
        }
        return listScripts(sender, folder, dir);
    }

    private int listDefault(ICommandSender sender) {
        List<ModSummary> summaries = LuaModManager.listModSummaries(luaRoot);
        if (!summaries.isEmpty()) {
            Path rootPath = luaRoot.toPath();
            sender.sendMessage("§6[LuaTweaker] Discovered LuaMods (§e" + summaries.size() + "§6):");
            for (ModSummary mod : summaries) {
                File modDir = rootPath.resolve(mod.id()).toFile();
                int scriptCount = collectLuaFiles(modDir).size();
                String stateColor = mod.enabled() ? "§a" : "§7";
                sender.sendMessage("  §7- §f" + mod.id() + " §7v" + mod.version()
                        + " " + stateColor + "[" + mod.statusLine() + "] §8(" + scriptCount + " .lua)");
            }
            return 1;
        }
        File legacyServer = new File(luaRoot, "server");
        if (legacyServer.isDirectory()) {
            return listScripts(sender, "server", legacyServer);
        }
        sender.sendMessage("§7[LuaTweaker] No LuaMods found in " + luaRoot.getName() + "/");
        return 1;
    }

    /**
     * Resolves a user-supplied path strictly inside the lua root. Absolute
     * paths and ".." escapes return null so any player (permission level 0)
     * cannot enumerate files outside the mod folder.
     */
    static File resolveInsideRoot(File root, String userPath) {
        if (userPath == null || userPath.isBlank() || Path.of(userPath).isAbsolute()) {
            return null;
        }
        try {
            File canonicalRoot = root.getCanonicalFile();
            File target = new File(root, userPath).getCanonicalFile();
            String prefix = canonicalRoot.getPath() + File.separator;
            if (!target.getPath().startsWith(prefix) && !target.equals(canonicalRoot)) {
                return null;
            }
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    private static int listScripts(ICommandSender sender, String label, File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            sender.sendError("Unknown script folder: lua/" + label);
            return 0;
        }
        List<Path> luaFiles = collectLuaFiles(dir);
        if (luaFiles.isEmpty()) {
            sender.sendMessage("§7[LuaTweaker] No scripts in " + label + "/");
            return 1;
        }
        Path base = dir.toPath();
        List<String> relativeNames = new ArrayList<>(luaFiles.size());
        for (Path file : luaFiles) {
            relativeNames.add(base.relativize(file).toString().replace('\\', '/'));
        }
        printScriptPaths(sender, label, relativeNames);
        return 1;
    }

    private List<String> collectPhaseScriptsAcrossMods(String phase) {
        List<String> found = new ArrayList<>();
        File[] modDirs = luaRoot.listFiles(File::isDirectory);
        if (modDirs == null) {
            return found;
        }
        Path rootPath = luaRoot.toPath();
        for (File modDir : modDirs) {
            if (STUBS_DIR.equals(modDir.getName())) {
                continue;
            }
            File phaseDir = modDir.toPath().resolve("src").resolve(phase).toFile();
            if (!phaseDir.isDirectory()) {
                continue;
            }
            for (Path file : collectLuaFiles(phaseDir)) {
                found.add(rootPath.relativize(file).toString().replace('\\', '/'));
            }
        }
        found.sort(String.CASE_INSENSITIVE_ORDER);
        return found;
    }

    static List<Path> collectLuaFiles(File dir) {
        if (!dir.isDirectory()) {
            return List.of();
        }
        Path root = dir.toPath();
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !containsStubSegment(root.relativize(p)))
                    .filter(p -> p.getFileName().toString().endsWith(".lua"))
                    .sorted(Comparator.comparing(p -> p.toString().toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean containsStubSegment(Path relative) {
        for (Path part : relative) {
            if (STUBS_DIR.equals(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static void printScriptPaths(ICommandSender sender, String label, List<String> paths) {
        sender.sendMessage("§6[LuaTweaker] Scripts in §e" + label + "§6 (" + paths.size() + "):");
        for (String path : paths) {
            sender.sendMessage("  §7- §f" + path);
        }
    }
}
