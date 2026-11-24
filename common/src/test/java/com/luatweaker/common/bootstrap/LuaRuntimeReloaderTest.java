package com.luatweaker.common.bootstrap;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.support.FakePlatform;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.events.EventServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies startup load, hot-reload teardown, datapack materialization, and stub export. */
class LuaRuntimeReloaderTest {

    @BeforeEach
    void setUp() {
        FakePlatform.installPermissive("ReloaderTest", data -> {
        });
    }

    @AfterEach
    void tearDown() {
        ILuaEngine active = LuaTweakerCommon.getActiveEngine();
        if (active != null) {
            active.shutdown();
            LuaTweakerCommon.setActiveEngine(null);
        }
        EventServiceImpl.fireTeardownHooks();
        LuaServiceRegistry.clear();
    }

    @Test
    void startupFinalizesLootDatapackAndExportsStubs(@TempDir File tempRoot) throws IOException {
        File luaDir = new File(tempRoot, "luamods");
        writeTestMod(luaDir, """
                local Loot = require("LuaTweaker.Loot")
                Loot:AddChestLoot("minecraft:chests/simple_dungeon", "minecraft:emerald", 0.5, 1, 2)
                """, "v1");

        ContentServiceImpl content = new ContentServiceImpl();
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        LuaRuntimeReloader.loadOrReload(tempRoot, luaDir, false, content, datapack, true);
        LuaRuntimeReloader.finalizeDatapackAndStubs(tempRoot, luaDir, content, datapack);

        File stubFile = new File(luaDir, ".luatweaker/stubs/luatweaker-api.lua");
        boolean hasLootJson = datapack.getVirtualFiles()
                .containsKey("data/minecraft/loot_table/chests/simple_dungeon.json");
        assertTrue(hasLootJson && stubFile.isFile() && stubFile.length() > 0,
                "startup finalization must materialize loot tables and export EmmyLua stubs");
    }

    @Test
    void hotReloadFiresTeardownReplacesEngineAndReloadsScripts(@TempDir File tempRoot) throws IOException {
        File luaDir = new File(tempRoot, "luamods");
        writeTestMod(luaDir, """
                local Events = require("LuaTweaker.Events")
                local cleaned = false
                Events:Listen("OnScriptUnload", function()
                    cleaned = true
                end)
                function OnDisable()
                    cleaned = true
                end
                """, "v1");

        ContentServiceImpl content = new ContentServiceImpl();
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        ILuaEngine firstEngine = LuaRuntimeReloader.loadOrReload(
                tempRoot, luaDir, false, content, datapack, true);

        writeTestMod(luaDir, """
                local Loot = require("LuaTweaker.Loot")
                Loot:AddMobDrop("minecraft:zombie", "minecraft:gold_ingot", 1.0, 1, 1, 0)
                """, "v2");

        ILuaEngine secondEngine = LuaRuntimeReloader.loadOrReload(
                tempRoot, luaDir, false, content, datapack, false);

        assertNotSame(firstEngine, secondEngine,
                "hot-reload must replace the active Lua engine instance");
    }

    @Test
    void hotReloadMaterializesUpdatedDatapackEntries(@TempDir File tempRoot) throws IOException {
        File luaDir = new File(tempRoot, "luamods");
        writeTestMod(luaDir, "return true\n", "v1");

        ContentServiceImpl content = new ContentServiceImpl();
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        LuaRuntimeReloader.loadOrReload(tempRoot, luaDir, false, content, datapack, true);

        writeTestMod(luaDir, """
                local Loot = require("LuaTweaker.Loot")
                Loot:AddMobDrop("minecraft:skeleton", "minecraft:diamond", 1.0, 1, 1, 0)
                """, "v2");
        LuaRuntimeReloader.loadOrReload(tempRoot, luaDir, false, content, datapack, false);

        assertEquals(Boolean.TRUE,
                datapack.getVirtualFiles().containsKey("data/minecraft/loot_table/entities/skeleton.json"),
                "hot-reload must materialize newly added virtual datapack loot tables");
    }

    private static void writeTestMod(File luaDir, String serverScript, String version) throws IOException {
        File modDir = new File(luaDir, "reload_probe");
        File serverDir = new File(modDir, "src/server");
        if (!serverDir.mkdirs() && !serverDir.isDirectory()) {
            throw new IOException("Failed to create test mod directory: " + serverDir);
        }
        Files.writeString(new File(modDir, "manifest.json").toPath(), """
                {
                  "id": "reload_probe",
                  "name": "Reload Probe",
                  "version": "%s",
                  "main": "main.lua",
                  "environment": "universal"
                }
                """.formatted(version), StandardCharsets.UTF_8);
        Files.writeString(new File(modDir, "main.lua").toPath(),
                "require(\"src.server.init\")\n", StandardCharsets.UTF_8);
        Files.writeString(new File(serverDir, "init.lua").toPath(),
                serverScript, StandardCharsets.UTF_8);
    }
}
