package com.luatweaker.platform;

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

    /** Finds the luamods directory containing real mods, or a sensible fallback. */
    public static File findLuamodsDir() {
        Path workingDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path dir = workingDir; dir != null; dir = dir.getParent()) {
            Path luamods = dir.resolve("luamods");
            if (containsMods(luamods)) {
                return luamods.toFile();
            }
        }
        return workingDir.resolve("luamods").toFile();
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
                Path candidate = mod.toPath().resolve(relativeSuffix);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toFile();
                }
            }
        }
        return luamodsDir.toPath().resolve(relativeSuffix).toFile();
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
