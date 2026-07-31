package com.luatweaker.core.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LtvmStubExporter {
    public static void exportToWorkspace(Path runDir, LtvmStubGenerator generator) {
        try {
            Path luamodsDir = runDir.resolve("luamods");

            if (!Files.exists(luamodsDir)) {
                Path parent = runDir.toAbsolutePath().getParent();
                if (parent != null) {
                    Path siblingLuamods = parent.resolve("luamods");
                    if (Files.exists(siblingLuamods)) {
                        luamodsDir = siblingLuamods;
                    }
                }
            }

            if (!Files.exists(luamodsDir)) {
                Files.createDirectories(luamodsDir);
            }

            Path stubDir = luamodsDir.resolve(".luatweaker/stubs");
            Files.createDirectories(stubDir);

            Path stubFile = stubDir.resolve("luatweaker-api.lua");
            Files.writeString(stubFile, generator.getResult());

            System.out.println("[LTVM] Successfully exported LSP stubs to: " + stubFile.toAbsolutePath());

            // Auto-generate .luarc.json for seamless VS Code Lua Language Server integration
            Path luaRcFile = luamodsDir.resolve(".luarc.json");
            if (!Files.exists(luaRcFile)) {
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
                System.out.println("[LTVM] Auto-generated VS Code LSP config: " + luaRcFile.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("[LTVM] Failed to export LSP stubs: " + e.getMessage());
        }
    }
}

