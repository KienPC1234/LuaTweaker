package com.luatweaker.platform;

import com.luatweaker.api.pal.IPlatformHelper;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.platform.bootstrap.LuaServiceBootstrap;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LuaScriptsIntegrationTest {

    private NeoForgeRecipeManager recipeManager;

    @BeforeEach
    public void setUp() {
        recipeManager = new NeoForgeRecipeManager();
        LuaServiceRegistry.clear();

        Platform.set(new IPlatformHelper() {
            @Override
            public com.luatweaker.api.objects.IItem createItem(String itemId, int count) {
                return new com.luatweaker.api.objects.IItem() {
                    @Override
                    public String getId() { return itemId; }
                    @Override
                    public int getCount() { return count; }
                    @Override
                    public boolean hasTag(String tagId) { return false; }
                    @Override
                    public Object getRawItemStack() { return null; }
                };
            }

            @Override
            public boolean itemExists(String itemId) { return true; }
            @Override
            public boolean blockExists(String blockId) { return true; }
            @Override
            public boolean fluidExists(String fluidId) { return true; }
            @Override
            public boolean tagExists(String tagId) { return true; }
        });
    }

    @AfterAll
    public static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    public void testAllNeoForgePlatformLuaScripts() {
        File baseDir = findLuaDir();
        assertTrue(baseDir.exists(), "Lua directory should exist at " + baseDir.getAbsolutePath());

        ILuaEngine engine = new CobaltLuaEngine(true);

        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();

        // Register all services using LuaServiceBootstrap
        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService, recipeManager);

        // 1. Startup scripts
        File startupDir = new File(baseDir, "startup");
        if (startupDir.exists()) {
            File[] files = startupDir.listFiles((dir, name) -> name.endsWith(".lua"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
                for (File script : files) {
                    assertDoesNotThrow(() -> engine.executeScript(script, "TEST-STARTUP"),
                            "Failed executing startup script: " + script.getName());
                }
            }
        }

        // 2. Server scripts
        File serverDir = new File(baseDir, "server");
        if (serverDir.exists()) {
            File[] files = serverDir.listFiles((dir, name) -> name.endsWith(".lua"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
                for (File script : files) {
                    assertDoesNotThrow(() -> engine.executeScript(script, "TEST-SERVER"),
                            "Failed executing server script: " + script.getName());
                }
            }
        }

        // 3. Client scripts
        File clientDir = new File(baseDir, "client");
        if (clientDir.exists()) {
            File[] files = clientDir.listFiles((dir, name) -> name.endsWith(".lua"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
                for (File script : files) {
                    assertDoesNotThrow(() -> engine.executeScript(script, "TEST-CLIENT"),
                            "Failed executing client script: " + script.getName());
                }
            }
        }
    }

    private File findLuaDir() {
        File[] candidates = new File[] {
                new File("neoforge-platform/lua"),
                new File("lua"),
                new File("../../neoforge-platform/lua"),
                new File("../../lua")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate;
            }
        }
        return candidates[0];
    }
}
