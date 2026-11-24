package com.luatweaker.common.command.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies ListCommand and SyntaxCommand against autonomous luamods/<mod_id>/ packages. */
class ListAndSyntaxLuamodsTest {

    @Test
    void defaultListShowsDiscoveredAutonomousMods(@TempDir File tempRoot) throws IOException {
        File luaRoot = new File(tempRoot, "luamods");
        createAutonomousMod(luaRoot, "alpha_mod", "local a = 1\nreturn a\n");
        createStubFile(luaRoot);

        MockCommandSender sender = new MockCommandSender();
        int exitCode = new ListCommand(luaRoot).execute(sender, new String[0]);

        assertTrue(exitCode == 1
                        && sender.messages.stream().anyMatch(msg -> msg.contains("alpha_mod")),
                "default /lt list must report discovered autonomous LuaMods: " + sender.messages);
    }

    @Test
    void phaseListFindsServerScriptsAcrossModsAndSkipsStubs(@TempDir File luaRoot) throws IOException {
        createAutonomousMod(luaRoot, "beta_mod", "return true\n");
        createStubFile(luaRoot);

        MockCommandSender sender = new MockCommandSender();
        int exitCode = new ListCommand(luaRoot).execute(sender, new String[]{"server"});

        assertTrue(exitCode == 1
                        && sender.messages.stream().anyMatch(msg -> msg.contains("beta_mod/src/server/main_srv.lua"))
                        && sender.messages.stream().noneMatch(msg -> msg.contains(".luatweaker")),
                "/lt list server must aggregate src/server scripts and skip .luatweaker stubs: " + sender.messages);
    }

    @Test
    void syntaxCheckSucceedsOnValidAutonomousMods(@TempDir File luaRoot) throws IOException {
        createAutonomousMod(luaRoot, "clean_mod", "local v = 42\nreturn v\n");
        createStubFile(luaRoot);

        MockCommandSender sender = new MockCommandSender();
        int exitCode = new SyntaxCommand(luaRoot).execute(sender, new String[0]);

        assertEquals(1, exitCode,
                "/lt syntax with no args must validate autonomous luamods scripts: " + sender.messages);
    }

    @Test
    void syntaxCheckFailsOnBrokenModScript(@TempDir File luaRoot) throws IOException {
        createAutonomousMod(luaRoot, "broken_mod", "function broken(\n");

        MockCommandSender sender = new MockCommandSender();
        int exitCode = new SyntaxCommand(luaRoot).execute(sender, new String[]{"broken_mod"});

        assertTrue(exitCode == 0 && sender.failedLoudly(),
                "/lt syntax <mod_id> must fail loudly on invalid Lua syntax: " + sender.messages);
    }

    private static void createAutonomousMod(File luaRoot, String modId, String serverLua) throws IOException {
        File modDir = new File(luaRoot, modId);
        File serverDir = new File(modDir, "src/server");
        if (!serverDir.mkdirs() && !serverDir.isDirectory()) {
            throw new IOException("Cannot create fixture directory: " + serverDir);
        }
        Files.writeString(new File(modDir, "manifest.json").toPath(), """
                {
                  "id": "%s",
                  "name": "%s",
                  "version": "1.0.0",
                  "main": "main.lua",
                  "environment": "universal"
                }
                """.formatted(modId, modId), StandardCharsets.UTF_8);
        Files.writeString(new File(modDir, "main.lua").toPath(),
                "require(\"src.server.main_srv\")\n", StandardCharsets.UTF_8);
        Files.writeString(new File(serverDir, "main_srv.lua").toPath(),
                serverLua, StandardCharsets.UTF_8);
    }

    private static void createStubFile(File luaRoot) throws IOException {
        File stubsDir = new File(luaRoot, ".luatweaker/stubs");
        if (!stubsDir.mkdirs() && !stubsDir.isDirectory()) {
            throw new IOException("Cannot create stub directory: " + stubsDir);
        }
        Files.writeString(new File(stubsDir, "api.lua").toPath(),
                "---@meta\ninvalid syntax if not skipped = (\n", StandardCharsets.UTF_8);
    }
}
