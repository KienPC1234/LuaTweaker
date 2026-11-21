package com.luatweaker.recipes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies per-mod logging and the auto-generated mods manager registry.
 */
class LuaModManagerLoggingTest {

    private static final String MOD_LOG_PATH = "logs/luatweaker/mods/test_mod.log";
    private static final String MANIFEST_JSON = """
        {
          "id": "test_mod",
          "name": "Test Mod",
          "author": "Tester",
          "version": "1.0.0",
          "environment": "universal",
          "main": "main.lua"
        }
        """;
    private static final String MAIN_LUA = """
        print("Hello from Test Mod dedicated log!")
        log.info("Test Mod structured log line")
        """;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private File arrangeMod() throws IOException {
        File testDir = new File("build/tmp/testLuaMods");
        File luamodsDir = new File(testDir, "luamods");
        File modDir = new File(luamodsDir, "test_mod");
        modDir.mkdirs();

        File manifestFile = new File(modDir, "manifest.json");
        Files.writeString(manifestFile.toPath(), MANIFEST_JSON);

        File mainFile = new File(modDir, "main.lua");
        Files.writeString(mainFile.toPath(), MAIN_LUA);

        ILuaEngine engine = LuaEngineFactory.create(true);
        com.luatweaker.core.mod.LuaModManager.loadLuaMods(luamodsDir, engine, "universal");
        return luamodsDir;
    }

    @Test
    void generatesModsManager() throws IOException {
        File luamodsDir = arrangeMod();

        File modsManagerFile = new File(luamodsDir, "mods_manager.json");
        assertTrue(modsManagerFile.exists(), "mods_manager.json should be auto-generated");
    }

    @Test
    void registersTestMod() throws IOException {
        File luamodsDir = arrangeMod();

        File modsManagerFile = new File(luamodsDir, "mods_manager.json");
        String managerContent = Files.readString(modsManagerFile.toPath());
        assertTrue(managerContent.contains("\"test_mod\": true"), "test_mod should be registered as true");
    }

    @Test
    void createsPerModLog() throws IOException {
        File modLogFile = new File(MOD_LOG_PATH);
        if (modLogFile.exists()) {
            modLogFile.delete();
        }
        arrangeMod();

        assertTrue(modLogFile.exists(), "Per-mod log file test_mod.log should exist");
    }

    @Test
    void logsHelloMessage() throws IOException {
        arrangeMod();

        String logContent = Files.readString(new File(MOD_LOG_PATH).toPath());
        assertTrue(logContent.contains("Hello from Test Mod dedicated log!"),
                "the per-mod log must capture print output");
    }

    @Test
    void logsStructuredLine() throws IOException {
        arrangeMod();

        String logContent = Files.readString(new File(MOD_LOG_PATH).toPath());
        assertTrue(logContent.contains("Test Mod structured log line"),
                "the per-mod log must capture structured log lines");
    }
}
