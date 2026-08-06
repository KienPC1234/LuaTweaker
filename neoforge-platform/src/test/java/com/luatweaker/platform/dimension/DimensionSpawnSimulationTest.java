package com.luatweaker.platform.dimension;

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
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.platform.bootstrap.LuaServiceBootstrap;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SIMULATION of the real in-game scenarios with the actual arcane_rpg mods:
 * the spawn/teleport targets, land availability, underwater columns and
 * skyland island coverage around the spawn points. This is the same logic the
 * chunk generator and the teleport use, so it catches "player spawned
 * underground / in the void / underwater" style bugs before they reach the
 * game.
 */
public class DimensionSpawnSimulationTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private DimensionServiceImpl service;
    private DimensionConfig realm;
    private DimensionConfig skylands;

    @BeforeEach
    public void setUp() {
        LuaServiceRegistry.clear();
        com.luatweaker.command.CommandServiceImpl.clear();
        Platform.setContent(new IPlatformContent() {
            @Override public com.luatweaker.api.objects.IItem createItem(String itemId, int count) { return null; }
            @Override public boolean itemExists(String itemId) { return false; }
            @Override public boolean blockExists(String blockId) { return false; }
            @Override public boolean fluidExists(String fluidId) { return false; }
            @Override public boolean tagExists(String tagId) { return false; }
            @Override public boolean isModLoaded(String modId) { return false; }
            @Override public boolean isClient() { return false; }
            @Override public boolean isDedicatedServer() { return true; }
            @Override public String getPlatformName() { return "SimulationTest"; }
            @Override public java.util.Set<String> getSupportedMobParents() { return java.util.Set.of(); }
            @Override public java.util.List<com.luatweaker.api.objects.IRecipe> getAllRecipes() { return java.util.List.of(); }
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
            public com.luatweaker.api.entity.IEntity shootProjectileAt(com.luatweaker.api.entity.IEntity s, String p, com.luatweaker.api.entity.IEntity t, double sp) { return null; }
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
            public java.util.List<com.luatweaker.api.entity.IEntity> getNearbyEntities(com.luatweaker.api.entity.IEntity c, double r) { return java.util.List.of(); }
            public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(String d, int x, int y, int z) { return null; }
            public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(Object e, int s) { return null; }
            public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(String u) { return null; }
            public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(Object e) { return null; }
            public java.util.Map<String, Object> getBlockState(String d, int x, int y, int z) { return null; }
            public boolean setBlockState(String d, int x, int y, int z, String b, java.util.Map<String, Object> p) { return false; }
            public java.util.Map<String, Object> getBlockEntityData(String d, int x, int y, int z) { return null; }
            public boolean setBlockEntityData(String d, int x, int y, int z, java.util.Map<String, Object> data) { return false; }
            public boolean ejectContainerItem(String d, int x, int y, int z, int slot, int count) { return false; }
            public boolean placeStructure(String d, String t, int x, int y, int z, int r) { return false; }
            public long fillBlocks(String d, int x1, int y1, int z1, int x2, int y2, int z2, String b, java.util.Map<String, Object> p) { return 0; }
            public long replaceBlocks(String d, int x1, int y1, int z1, int x2, int y2, int z2, String f, String t) { return 0; }
            public boolean executeCommand(String c) { return false; }
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

        File baseDir = findLuamodsDir();
        System.out.println("[SimTest] baseDir=" + baseDir.getAbsolutePath() + " exists=" + baseDir.exists());
        ILuaEngine engine = new CobaltLuaEngine(true);
        engine.setLuaDirectory(baseDir);
        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();
        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService,
                new NeoForgeRecipeManager());
        assertDoesNotThrow(() -> LuaModManager.loadLuaMods(baseDir, engine, "universal"));

        service = (DimensionServiceImpl) LuaServiceRegistry.get("DimensionServiceImpl");
        assertNotNull(service);
        System.out.println("[SimTest] registered dimension ids: " + service.getDimensionIds());
        System.out.println("[SimTest] noise service: "
                + (LuaServiceRegistry.get("NoiseServiceImpl") != null ? "present" : "MISSING"));
        realm = service.getConfig("luatweaker:crystal_realm");
        skylands = service.getConfig("luatweaker:crystal_skylands");
        assertNotNull(realm, "crystal_realm must be registered");
        assertNotNull(skylands, "crystal_skylands must be registered");
    }

    /** Simulates the teleport target search around the configured spawn point. */
    private static class SpawnResult {
        boolean foundLand;
        int landX;
        int landZ;
        int landHeight;
        int ring;
        int underwaterColumnsInRing0;
    }

    private SpawnResult simulateSpawnSearch(DimensionConfig cfg, int spawnX, int spawnZ) {
        SpawnResult result = new SpawnResult();
        result.foundLand = false;
        result.underwaterColumnsInRing0 = 0;
        outer:
        for (int ring = 0; ring <= 16; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = spawnX + dx;
                    int cz = spawnZ + dz;
                    DimensionServiceImpl.SurfaceResult s = service.computeSurface(cfg.id(), cx, cz, cfg.seaLevel());
                    if (s.height() <= cfg.minHeight() + 1) continue;
                    boolean underwater = s.height() < cfg.seaLevel();
                    if (ring == 0 && underwater) result.underwaterColumnsInRing0++;
                    if (underwater) continue;
                    result.foundLand = true;
                    result.landX = cx;
                    result.landZ = cz;
                    result.landHeight = s.height();
                    result.ring = ring;
                    break outer;
                }
            }
        }
        return result;
    }

    @Test
    public void crystalRealm_SpawnPointHasLandNearbyAndNotUnderwater() {
        int spawnX = realm.spawnX() != null ? realm.spawnX() : 0;
        int spawnZ = realm.spawnZ() != null ? realm.spawnZ() : 0;

        SpawnResult result = simulateSpawnSearch(realm, spawnX, spawnZ);

        assertTrue(result.foundLand, "teleport must find a land column near spawn ("
                + spawnX + "," + spawnZ + ")");
        assertTrue(result.ring <= 2, "land should be within 2 rings of the spawn point, found at ring " + result.ring);
        assertTrue(result.landHeight >= realm.minHeight() + 2 && result.landHeight < realm.maxHeight(),
                "land height out of world bounds: " + result.landHeight);
        assertTrue(result.landHeight >= realm.seaLevel(),
                "land column must be above sea level (not underwater), got " + result.landHeight
                        + " (seaLevel " + realm.seaLevel() + ")");
    }

    @Test
    public void skylands_SpawnPointHasIslandNearby() {
        int spawnX = skylands.spawnX() != null ? skylands.spawnX() : 0;
        int spawnZ = skylands.spawnZ() != null ? skylands.spawnZ() : 0;

        SpawnResult result = simulateSpawnSearch(skylands, spawnX, spawnZ);

        assertTrue(result.foundLand, "skylands teleport must find an island near spawn ("
                + spawnX + "," + spawnZ + ")");
        assertTrue(result.ring <= 4, "island should be close to the spawn point, found at ring " + result.ring);
        assertTrue(result.landHeight >= 180,
                "island top must be at the skyland altitude, got " + result.landHeight);
        assertTrue(result.landHeight < skylands.maxHeight(), "island above world height: " + result.landHeight);
    }

    @Test
    public void crystalRealm_WholeWorldIsCoveredWithTerrain() {
        // A 512x512 area around spawn must be almost entirely solid terrain
        // (no void, no holes) with heights inside the world bounds.
        int solid = 0;
        int total = 0;
        for (int z = -256; z <= 256; z += 8) {
            for (int x = -256; x <= 256; x += 8) {
                total++;
                DimensionServiceImpl.SurfaceResult s = service.computeSurface(realm.id(), x, z, realm.seaLevel());
                if (s.height() > realm.minHeight() + 1) {
                    solid++;
                    assertTrue(s.height() >= realm.minHeight() + 1 && s.height() < realm.maxHeight(),
                            "height out of bounds at (" + x + "," + z + "): " + s.height());
                }
            }
        }
        assertTrue(solid * 100.0 / total > 95.0,
                "realm must be mostly solid terrain, got " + solid + "/" + total + " solid columns");
    }

    @Test
    public void skylands_HasIslandsAndVoidAcrossTheWorld() {
        int islands = 0;
        int voids = 0;
        int total = 0;
        for (int z = -256; z <= 256; z += 4) {
            for (int x = -256; x <= 256; x += 4) {
                total++;
                DimensionServiceImpl.SurfaceResult s = service.computeSurface(skylands.id(), x, z, skylands.seaLevel());
                if (s.height() <= skylands.minHeight() + 1) {
                    voids++;
                } else {
                    islands++;
                    assertTrue(s.height() >= 180,
                            "island too low at (" + x + "," + z + "): " + s.height());
                    // The Lua picker carves air below the island slab.
                    Map<Integer, String> overrides = service.computeBlockOverrides(
                            skylands.id(), x, z, s.height(), skylands.minHeight(), 320);
                    int lowestSolid = Integer.MAX_VALUE;
                    for (Map.Entry<Integer, String> entry : overrides.entrySet()) {
                        if (!"minecraft:air".equals(entry.getValue())) {
                            lowestSolid = Math.min(lowestSolid, entry.getKey());
                        }
                    }
                    assertTrue(lowestSolid >= s.height() - 20,
                            "island slab must be thin at (" + x + "," + z + "): top=" + s.height()
                                    + " lowestSolid=" + lowestSolid);
                }
            }
        }
        assertTrue(islands * 100.0 / total > 10.0,
                "skylands must have islands, got " + islands + "/" + total);
        assertTrue(voids > 0, "skylands must have void between islands");
    }

    @Test
    public void lakeBasinsAreBelowSeaLevel() {
        // Water lakes are made in Lua by lowering the surface below sea level
        // so the engine's water fill creates the lake surface.
        for (int z = -64; z <= 64; z += 2) {
            for (int x = -64; x <= 64; x += 2) {
                DimensionServiceImpl.SurfaceResult s = service.computeSurface(realm.id(), x, z, realm.seaLevel());
                if (s.height() < realm.seaLevel()) {
                    assertTrue(s.height() >= realm.minHeight() + 1,
                            "lake basin must stay inside world bounds at (" + x + "," + z + ")");
                }
            }
        }
    }

    @Test
    public void terrainIsDeterministicAcrossSimulations() {
        DimensionServiceImpl.SurfaceResult a = service.computeSurface(realm.id(), 123, -456, realm.seaLevel());
        DimensionServiceImpl.SurfaceResult b = service.computeSurface(realm.id(), 123, -456, realm.seaLevel());
        assertEquals(a.height(), b.height());
        assertEquals(a.blockId(), b.blockId());
    }

    private File findLuamodsDir() {
        return com.luatweaker.platform.TestPaths.findLuamodsDir();
    }
}
