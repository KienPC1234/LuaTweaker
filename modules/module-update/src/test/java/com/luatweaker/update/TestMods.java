package com.luatweaker.update;

import com.luatweaker.core.mod.LuaModManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Shared test helpers: create temp luamods dirs and load them through the
 * real LuaModManager against the FakeEngine.
 */
public final class TestMods {
    private TestMods() {}

    public static File createLuamodsDir(File tempDir) throws IOException {
        File luamods = new File(tempDir, "luamods");
        Files.createDirectories(luamods.toPath());
        return luamods;
    }

    public static void loadMod(File luamodsDir, FakeEngine engine, String modId, String... permissions)
            throws IOException {
        writeManifest(luamodsDir, modId, null, permissions);
        LuaModManager.loadLuaMods(luamodsDir, engine, "universal");
    }

    public static void loadModWithUpdateUrl(File luamodsDir, FakeEngine engine, String modId,
                                            String updateUrl, String... permissions) throws IOException {
        writeManifest(luamodsDir, modId, updateUrl, permissions);
        LuaModManager.loadLuaMods(luamodsDir, engine, "universal");
    }

    public static File writeManifest(File luamodsDir, String modId, String updateUrl, String... permissions)
            throws IOException {
        File modDir = new File(luamodsDir, modId);
        Files.createDirectories(modDir.toPath());
        StringBuilder perms = new StringBuilder();
        for (String p : permissions) {
            if (perms.length() > 0) perms.append(", ");
            perms.append("\"").append(p).append("\"");
        }
        String updateField = updateUrl == null ? "" : ",\n  \"update_url\": \"" + updateUrl + "\"";
        String manifest = "{\n"
                + "  \"id\": \"" + modId + "\",\n"
                + "  \"name\": \"" + modId + "\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"main\": \"main.lua\",\n"
                + "  \"permissions\": [" + perms + "],\n"
                + "  \"environment\": \"universal\""
                + updateField
                + "\n}\n";
        Files.writeString(new File(modDir, "manifest.json").toPath(), manifest, StandardCharsets.UTF_8);
        return modDir;
    }
}
