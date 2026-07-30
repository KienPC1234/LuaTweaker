package com.luatweaker.core.lsp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LtvmStubExporter {
    public static void exportToWorkspace(Path runDir, LtvmStubGenerator generator) {
        try {
            Path luaDir = runDir.resolve("lua");
            
            // Fallback for development if run/ folder exists but lua folder is at the workspace root
            if (!Files.exists(luaDir)) {
                Path parent = runDir.toAbsolutePath().getParent();
                if (parent != null) {
                    Path siblingLua = parent.resolve("lua");
                    if (Files.exists(siblingLua)) {
                        luaDir = siblingLua;
                    }
                }
            }
            
            Path stubDir = luaDir.resolve(".luatweaker/stubs");
            Files.createDirectories(stubDir);
            
            Path stubFile = stubDir.resolve("luatweaker-api.lua");
            Files.writeString(stubFile, generator.getResult());
            
            System.out.println("[LTVM] Successfully exported LSP stubs to: " + stubFile.toAbsolutePath());

            // Auto-generate .luarc.json for seamless VS Code Lua Language Server integration
            Path luaRcFile = luaDir.resolve(".luarc.json");
            if (!Files.exists(luaRcFile)) {
                String luaRcContent = """
                    {
                      "$schema": "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json",
                      "Lua.workspace.library": [
                        ".luatweaker/stubs"
                      ],
                      "Lua.diagnostics.globals": [
                        "Mod",
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

