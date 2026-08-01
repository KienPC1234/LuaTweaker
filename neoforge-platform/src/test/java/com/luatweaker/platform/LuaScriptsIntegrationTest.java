package com.luatweaker.platform;

import com.luatweaker.api.pal.IPlatformContent;
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

        Platform.setContent(new IPlatformContent() {
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
            @Override
            public boolean isModLoaded(String modId) { return true; }
            @Override
            public boolean isClient() { return false; }
            @Override
            public boolean isDedicatedServer() { return true; }
            @Override
            public String getPlatformName() { return "IntegrationTest"; }
            @Override
            public java.util.Set<String> getSupportedMobParents() { return java.util.Set.of(); }
            @Override
            public java.util.List<com.luatweaker.api.objects.IRecipe> getAllRecipes() { return java.util.List.of(); }
        });

        Platform.setNetwork(new com.luatweaker.api.pal.IPlatformNetwork() {
            public void sendPayloadPacket(String u, String c, String d) {}
            public void broadcastPayloadPacket(String c, String d) {}
            public void sendPayloadPacketToServer(String c, String d) {}
        });

        Platform.setStorage(new com.luatweaker.api.pal.IPlatformStorage() {
            public java.io.File getStorageDirectory() { return new java.io.File("."); }
        });

        Platform.setInteraction(new com.luatweaker.api.pal.IPlatformInteraction() {
            public void shootProjectile(com.luatweaker.api.entity.IEntity s, String p, double sp, double i) {}
            public void shootProjectileAt(com.luatweaker.api.entity.IEntity s, String p, com.luatweaker.api.entity.IEntity t, double sp) {}
            public void playAnimation(com.luatweaker.api.entity.IEntity e, String a, double sp, double tr) {}
            public boolean performBlockBreak(com.luatweaker.api.entity.IEntity a, int x, int y, int z) { return false; }
            public boolean performBlockPlace(com.luatweaker.api.entity.IEntity a, int x, int y, int z, String b) { return false; }
            public boolean performBlockUse(com.luatweaker.api.entity.IEntity a, int x, int y, int z) { return false; }
            public boolean performItemUse(com.luatweaker.api.entity.IEntity a, int s) { return false; }
            public void lookAt(com.luatweaker.api.entity.IEntity a, double x, double y, double z) {}
            public void lookAt(com.luatweaker.api.entity.IEntity a, com.luatweaker.api.entity.IEntity t) {}
            public boolean moveInventoryItem(com.luatweaker.api.entity.IEntity a, int f, int t) { return false; }
            public boolean dropInventoryItem(com.luatweaker.api.entity.IEntity a, int s, int c) { return false; }
            public java.util.List<com.luatweaker.api.objects.IWorldBlock> getNearbyBlocks(com.luatweaker.api.entity.IEntity e, int r) { return java.util.List.of(); }
            public java.util.List<com.luatweaker.api.objects.ILocatedItem> getInventoryItems(com.luatweaker.api.entity.IEntity e) { return java.util.List.of(); }
            public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(String d, int x, int y, int z) { return null; }
            public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(Object e, int s) { return null; }
            public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(String u) { return null; }
            public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(Object e) { return null; }
        });

        Platform.setEntity(new com.luatweaker.api.pal.IPlatformEntity() {
            public void addCustomGoal(com.luatweaker.api.entity.IEntity e, int p, com.luatweaker.api.vm.ILuaTable g, com.luatweaker.api.vm.ILuaEngine en, boolean i) {}
            public void removeCustomGoal(com.luatweaker.api.entity.IEntity e, com.luatweaker.api.vm.ILuaTable g) {}
            public void clearCustomGoals(com.luatweaker.api.entity.IEntity e) {}
            public void addMeleeAttackGoal(com.luatweaker.api.entity.IEntity e, int p, double s, boolean m) {}
            public void addHurtByTargetGoal(com.luatweaker.api.entity.IEntity e, int p) {}
            public void addNearestAttackableTargetGoal(com.luatweaker.api.entity.IEntity e, int p, String t) {}
            public com.luatweaker.api.entity.IPlayer getPlayer(String u) { return null; }
            public java.util.List<com.luatweaker.api.entity.IPlayer> getAllPlayers() { return java.util.List.of(); }
            public Object spawnEntity(String e, double x, double y, double z) { return null; }
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
