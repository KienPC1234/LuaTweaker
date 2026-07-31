package com.luatweaker.platform;

import com.luatweaker.api.pal.IPlatformHelper;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.platform.bootstrap.LuaServiceBootstrap;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

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
    public void testAllAutonomousLuaMods() {
        File baseDir = findLuamodsDir();
        assertTrue(baseDir.exists(), "luamods directory should exist at " + baseDir.getAbsolutePath());

        ILuaEngine engine = new CobaltLuaEngine(true);
        engine.setLuaDirectory(baseDir);

        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();

        // Register all services using LuaServiceBootstrap
        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService, recipeManager);

        assertDoesNotThrow(() -> LuaModManager.loadLuaMods(baseDir, engine),
                "Failed executing autonomous LuaMods in " + baseDir.getAbsolutePath());

        // Verify Network RemoteEvent Firing & Execution
        Object netService = LuaServiceRegistry.get("NetworkServiceImpl");
        assertTrue(netService instanceof com.luatweaker.network.NetworkServiceImpl, "NetworkServiceImpl should be registered");

        com.luatweaker.network.NetworkServiceImpl ns = (com.luatweaker.network.NetworkServiceImpl) netService;
        assertDoesNotThrow(() -> ns.OnClientFired("StaffSwapSkill", "dummy-uuid", new com.luatweaker.api.vm.ILuaValue[0]),
                "StaffSwapSkill packet firing failed");
        assertDoesNotThrow(() -> ns.OnClientFired("StaffCastSkill", "dummy-uuid", new com.luatweaker.api.vm.ILuaValue[0]),
                "StaffCastSkill packet firing failed");
    }

    private File findLuamodsDir() {
        File[] candidates = new File[] {
                new File("neoforge-platform/luamods"),
                new File("luamods"),
                new File("../../neoforge-platform/luamods"),
                new File("../../luamods")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate;
            }
        }
        return candidates[0];
    }
}
