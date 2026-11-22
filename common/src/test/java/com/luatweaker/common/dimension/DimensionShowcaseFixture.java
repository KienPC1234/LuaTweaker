package com.luatweaker.common.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.BiomesServiceImpl;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.common.LuamodPaths;
import com.luatweaker.common.bootstrap.LuaServiceBootstrap;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.common.support.FakePlatform;
import com.luatweaker.spawn.SpawnRuleServiceImpl;

import java.io.File;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Loads the real showcase LuaMods once and exposes the dimension pipeline for
 * every dimension test class: config parsing, terrain/biome callbacks, portal
 * mapping, virtual datapack biomes, and the spawn-point search used by teleport.
 */
final class DimensionShowcaseFixture {

    static final String REALM_ID = "luatweaker:crystal_realm";
    static final String SKYLANDS_ID = "luatweaker:crystal_skylands";

    private static final int MAX_SEARCH_RING = 16;
    private static final int SURFACE_MARGIN = 1;

    private static final ReentrantLock LOAD_LOCK = new ReentrantLock();
    private static boolean loaded;
    private static ContentServiceImpl contentService;
    private static DatapackServiceImpl datapackService;
    private static DimensionServiceImpl service;
    private static BiomesServiceImpl biomesService;
    private static SpawnRuleServiceImpl spawnRuleService;
    private static DimensionConfig realm;
    private static DimensionConfig skylands;
    private static File baseDir;

    private DimensionShowcaseFixture() {
    }

    static void ensureLoaded() {
        LOAD_LOCK.lock();
        try {
            if (loaded) {
                restoreServices();
                return;
            }
            FakePlatform.installMinimal("DimensionIntegrationTest");
            LuaServiceRegistry.clear();
            com.luatweaker.command.CommandServiceImpl.clear();

            baseDir = LuamodPaths.findLuamodsDir();
            ILuaEngine engine = LuaEngineFactory.create(true);
            engine.setLuaDirectory(baseDir);
            contentService = new ContentServiceImpl();
            StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
            datapackService = new DatapackServiceImpl();

            LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService,
                    new CommonRecipeManager());
            LuaModManager.loadLuaMods(baseDir, engine, "universal");

            Object serviceObj = LuaServiceRegistry.get("DimensionServiceImpl");
            if (serviceObj instanceof DimensionServiceImpl dimensions) {
                service = dimensions;
                realm = service.getConfig(REALM_ID);
                skylands = service.getConfig(SKYLANDS_ID);
            }
            Object biomesObj = LuaServiceRegistry.get("BiomesServiceImpl");
            if (biomesObj instanceof BiomesServiceImpl biomes) {
                biomesService = biomes;
            }
            Object spawnObj = LuaServiceRegistry.get("SpawnRuleServiceImpl");
            if (spawnObj instanceof SpawnRuleServiceImpl spawnRules) {
                spawnRuleService = spawnRules;
            }
            loaded = true;
        } finally {
            LOAD_LOCK.unlock();
        }
    }

    /**
     * Sibling dimension tests clear the shared {@link LuaServiceRegistry} before
     * creating their own engine. When this fixture is already loaded, put the
     * services registered by its LuaMod load back so registry consumers still
     * observe the real, loaded instances.
     */
    private static void restoreServices() {
        if (service != null) {
            LuaServiceRegistry.register("DimensionServiceImpl", service);
        }
        if (biomesService != null) {
            LuaServiceRegistry.register("BiomesServiceImpl", biomesService);
        }
        if (spawnRuleService != null) {
            LuaServiceRegistry.register("SpawnRuleServiceImpl", spawnRuleService);
        }
    }

    static File getBaseDir() {
        return baseDir;
    }

    static ContentServiceImpl getContentService() {
        return contentService;
    }

    static DatapackServiceImpl getDatapackService() {
        return datapackService;
    }

    static DimensionServiceImpl getService() {
        return service;
    }

    static DimensionConfig getRealm() {
        return realm;
    }

    static DimensionConfig getSkylands() {
        return skylands;
    }

    static Map<String, String> getLoadErrors() {
        return LuaModManager.getLoadErrors();
    }

    static Object getBiomeService() {
        return LuaServiceRegistry.get("BiomesServiceImpl");
    }

    static File findModFile(String relativeSuffix) {
        return LuamodPaths.findModFile(getBaseDir(), relativeSuffix);
    }

    /** Simulates the teleport target search around the configured spawn point. */
    static SpawnResult simulateSpawnSearch(DimensionConfig cfg, int spawnX, int spawnZ) {
        SpawnResult result = new SpawnResult();
        for (int ring = 0; ring <= MAX_SEARCH_RING; ring++) {
            if (scanRing(cfg, spawnX, spawnZ, ring, result)) {
                return result;
            }
        }
        return result;
    }

    private static boolean scanRing(DimensionConfig cfg, int spawnX, int spawnZ,
            int ring, SpawnResult result) {
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                if (isRingEdge(dx, dz, ring)
                        && checkColumn(cfg, spawnX + dx, spawnZ + dz, ring, result)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRingEdge(int dx, int dz, int ring) {
        return Math.max(Math.abs(dx), Math.abs(dz)) == ring;
    }

    private static boolean checkColumn(DimensionConfig cfg, int cx, int cz,
            int ring, SpawnResult result) {
        DimensionServiceImpl.SurfaceResult surface =
                getService().computeSurface(cfg.id(), cx, cz, cfg.seaLevel());
        if (surface.height() <= cfg.minHeight() + SURFACE_MARGIN) {
            return false;
        }
        if (surface.height() < cfg.seaLevel()) {
            return false;
        }
        result.foundLand = true;
        result.landHeight = surface.height();
        result.ring = ring;
        return true;
    }

    /** The outcome of the teleport target search. */
    static final class SpawnResult {
        boolean foundLand;
        int landHeight;
        int ring;
    }
}
