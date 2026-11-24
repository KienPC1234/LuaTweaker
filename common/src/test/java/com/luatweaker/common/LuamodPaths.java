package com.luatweaker.common;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test-only path discovery: locates the luamods directory generically by
 * walking up from the process working directory (which the NeoForge moddev
 * plugin redirects to {@code build/minecraft-junit/}), with NO hardcoded
 * paths. A directory qualifies when it contains at least one mod folder
 * (a sub-directory with a manifest.json), so empty placeholder dirs created
 * by earlier failed lookups are never picked.
 */
public final class LuamodPaths {

    private LuamodPaths() {
    }

    private static final String LUAMODS_DIR_NAME = "luamods";

    /** Finds the luamods directory containing real mods, or a sensible fallback. */
    public static File findLuamodsDir() {
        Path workingDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path dir = workingDir; dir != null; dir = dir.getParent()) {
            Path luamods = dir.resolve(LUAMODS_DIR_NAME);
            if (containsMods(luamods)) {
                return luamods.toFile();
            }
            Path runLuamods = dir.resolve("run").resolve(LUAMODS_DIR_NAME);
            if (containsMods(runLuamods)) {
                return runLuamods.toFile();
            }
        }
        return workingDir.resolve(LUAMODS_DIR_NAME).toFile();
    }

    /**
     * Finds a file by name anywhere under {@code luamods/<mod>/<relativeSuffix>}
     * (e.g. "default_config.json") by scanning every mod folder - no mod id
     * is hardcoded.
     */
    public static File findModFile(File luamodsDir, String relativeSuffix) {
        File[] mods = luamodsDir.listFiles(File::isDirectory);
        if (mods != null) {
            for (File mod : mods) {
                File found = resolveModCandidate(mod.toPath(), relativeSuffix);
                if (found != null) {
                    return found;
                }
            }
        }
        return luamodsDir.toPath().resolve(relativeSuffix).toFile();
    }

    private static File resolveModCandidate(Path modPath, String relativeSuffix) {
        Path candidate = modPath.resolve(relativeSuffix);
        if (Files.isRegularFile(candidate)) {
            return candidate.toFile();
        }
        if (relativeSuffix.contains("/structures/")) {
            return resolveStructureCandidate(modPath, relativeSuffix);
        }
        return null;
    }

    private static File resolveStructureCandidate(Path modPath, String relativeSuffix) {
        Path alt = modPath.resolve(
                relativeSuffix.replace("/structures/", "/structure/structures/"));
        if (Files.isRegularFile(alt)) {
            return alt.toFile();
        }
        Path alt2 = modPath.resolve(
                relativeSuffix.replace("/structures/", "/structure/"));
        if (Files.isRegularFile(alt2)) {
            return alt2.toFile();
        }
        return null;
    }

    private static boolean containsMods(Path luamods) {
        File[] mods = luamods.toFile().listFiles(File::isDirectory);
        if (mods == null) {
            return false;
        }
        for (File mod : mods) {
            if (Files.isRegularFile(mod.toPath().resolve("manifest.json"))) {
                return true;
            }
        }
        return false;
    }
}
