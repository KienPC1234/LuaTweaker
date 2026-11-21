package com.luatweaker.core.lsp;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LtvmStubExporter {
    private LtvmStubExporter() {}

    public static void exportToWorkspace(Path runDir, LtvmStubGenerator generator) {
        try {
            Path luamodsDir = resolveLuamodsDir(runDir);

            if (!Files.exists(luamodsDir)) {
                Files.createDirectories(luamodsDir);
            }

            Path stubDir = luamodsDir.resolve(".luatweaker/stubs");
            Files.createDirectories(stubDir);

            Path stubFile = stubDir.resolve("luatweaker-api.lua");
            Files.writeString(stubFile, generator.getResult());

            LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[LTVM] Successfully exported LSP stubs to: " + stubFile.toAbsolutePath());

            writeLuaRcConfig(luamodsDir);
        } catch (IOException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                "[LTVM] Failed to export LSP stubs: " + e.getMessage());
        }
    }

    /**
     * Prefers an existing {@code luamods} next to the run dir (dev workspace
     * layout) and falls back to {@code runDir/luamods}.
     */
    private static Path resolveLuamodsDir(Path runDir) {
        Path luamodsDir = runDir.resolve("luamods");
        if (Files.exists(luamodsDir)) {
            return luamodsDir;
        }
        Path parent = runDir.toAbsolutePath().getParent();
        if (parent == null) {
            return luamodsDir;
        }
        Path siblingLuamods = parent.resolve("luamods");
        return Files.exists(siblingLuamods) ? siblingLuamods : luamodsDir;
    }

    /** Auto-generates .luarc.json for seamless VS Code Lua Language Server integration. */
    private static void writeLuaRcConfig(Path luamodsDir) throws IOException {
        Path luaRcFile = luamodsDir.resolve(".luarc.json");
        if (Files.exists(luaRcFile)) {
            return;
        }
        String luaRcContent = """
            {
              "$schema": "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json",
              "Lua.workspace.library": [
                ".luatweaker/stubs"
              ],
              "Lua.diagnostics.globals": [
                "Mod",
                "mod",
                "game",
                "item",
                "ingredient"
              ]
            }
            """;
        Files.writeString(luaRcFile, luaRcContent);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
            "[LTVM] Auto-generated VS Code LSP config: " + luaRcFile.toAbsolutePath());
    }
}

