package com.luatweaker.platform;

import java.io.File;

/**
 * Test-only path discovery: locates the luamods directory generically by
 * walking up from the process working directory (which the NeoForge moddev
 * plugin redirects to {@code build/minecraft-junit/}), with NO hardcoded
 * paths. A directory qualifies when it contains at least one mod folder
 * (a sub-directory with a manifest.json), so empty placeholder dirs created
 * by earlier failed lookups are never picked.
 */
public final class TestPaths {

    private TestPaths() {}

    /** Finds the luamods directory containing real mods, or a sensible fallback. */
    public static File findLuamodsDir() {
        File cwd = new File(System.getProperty("user.dir", "."));
        for (File dir = cwd; dir != null; dir = dir.getParentFile()) {
            File luamods = new File(dir, "luamods");
            if (containsMods(luamods)) {
                return luamods;
            }
        }
        return new File(cwd, "luamods");
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
                File candidate = new File(mod, relativeSuffix);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return new File(luamodsDir, relativeSuffix);
    }

    private static boolean containsMods(File luamods) {
        File[] mods = luamods.listFiles(File::isDirectory);
        if (mods == null) return false;
        for (File mod : mods) {
            if (new File(mod, "manifest.json").isFile()) {
                return true;
            }
        }
        return false;
    }
}
