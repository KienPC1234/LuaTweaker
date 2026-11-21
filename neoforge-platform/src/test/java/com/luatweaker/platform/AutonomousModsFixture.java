package com.luatweaker.platform;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.platform.bootstrap.LuaServiceBootstrap;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import com.luatweaker.platform.support.FakePlatform;

import java.io.File;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Loads the real autonomous LuaMods once and exposes the pipelines the
 * integration tests exercise: dimensions, spawn rules, network and recipes.
 */
final class AutonomousModsFixture {

    static final String GRAND_WILDS = "luatweaker:grand_wilds";
    static final String ALPINE_SPEC = "luatweaker:alpine_spec";
    static final String SKY_ISLES = "luatweaker:sky_isles";

    private static final int RIVER_SCAN_EXTENT = 48;
    private static final int RIVER_SCAN_STEP = 4;
    private static final int SEA_LEVEL = 62;
    private static final int SHALLOW_WATER_MAX = 48;

    private static final ReentrantLock LOAD_LOCK = new ReentrantLock();
    private static boolean loaded;
    private static File baseDir;
    private static ILuaEngine engine;
    private static DimensionServiceImpl dimensions;
    private static com.luatweaker.spawn.SpawnRuleServiceImpl spawnRules;
    private static com.luatweaker.network.NetworkServiceImpl networkService;
    private static com.luatweaker.noise.NoiseServiceImpl noiseService;

    private AutonomousModsFixture() {
    }

    static void ensureLoaded() {
        LOAD_LOCK.lock();
        try {
            if (loaded) {
                restoreServices();
                return;
            }
            FakePlatform.installPermissive("IntegrationTest", data -> {
            });
            LuaServiceRegistry.clear();
            com.luatweaker.command.CommandServiceImpl.clear();

            baseDir = LuamodPaths.findLuamodsDir();
            engine = LuaEngineFactory.create(true);
            engine.setLuaDirectory(baseDir);

            ContentServiceImpl contentService = new ContentServiceImpl();
            StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
            DatapackServiceImpl datapackService = new DatapackServiceImpl();
            NeoForgeRecipeManager recipeManager = new NeoForgeRecipeManager();

            LuaServiceBootstrap.registerAllServices(engine, contentService, storageService,
                    datapackService, recipeManager);
            LuaModManager.loadLuaMods(baseDir, engine, "universal");

            Object dimensionService = LuaServiceRegistry.get("DimensionServiceImpl");
            if (dimensionService instanceof DimensionServiceImpl dimensionImpl) {
                dimensions = dimensionImpl;
            }
            Object spawnObj = LuaServiceRegistry.get("SpawnRuleServiceImpl");
            if (spawnObj instanceof com.luatweaker.spawn.SpawnRuleServiceImpl spawnImpl) {
                spawnRules = spawnImpl;
            }
            Object netService = LuaServiceRegistry.get("NetworkServiceImpl");
            if (netService instanceof com.luatweaker.network.NetworkServiceImpl netImpl) {
                networkService = netImpl;
            }
            Object noiseObj = LuaServiceRegistry.get("NoiseServiceImpl");
            if (noiseObj instanceof com.luatweaker.noise.NoiseServiceImpl noiseImpl) {
                noiseService = noiseImpl;
            }
            loaded = true;
        } finally {
            LOAD_LOCK.unlock();
        }
    }

    /**
     * Other test classes clear the shared {@link LuaServiceRegistry}; when this
     * fixture is already loaded it must put its own loaded services back so the
     * terrain tests keep using the noise seed and dimension configs the LuaMods
     * registered, not a fresh service from an unrelated engine.
     */
    private static void restoreServices() {
        if (dimensions != null) {
            LuaServiceRegistry.register("DimensionServiceImpl", dimensions);
        }
        if (spawnRules != null) {
            LuaServiceRegistry.register("SpawnRuleServiceImpl", spawnRules);
        }
        if (networkService != null) {
            LuaServiceRegistry.register("NetworkServiceImpl", networkService);
        }
        if (noiseService != null) {
            LuaServiceRegistry.register("NoiseServiceImpl", noiseService);
        }
    }

    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    static File getBaseDir() {
        return baseDir;
    }

    static ILuaEngine getEngine() {
        return engine;
    }

    static DimensionServiceImpl getDimensions() {
        return dimensions;
    }

    static com.luatweaker.spawn.SpawnRuleServiceImpl getSpawnRules() {
        return spawnRules;
    }

    static com.luatweaker.network.NetworkServiceImpl getNetworkService() {
        return networkService;
    }

    static Map<String, String> getLoadErrors() {
        return LuaModManager.getLoadErrors();
    }

    /**
     * Finds a chunk with shallow river water next to dry land and returns the chunk
     * coordinate where a window should start so that chunk sits in the middle.
     */
    static int[] findRiverWindow(DimensionServiceImpl dimensionService, int windowRadius) {
        for (int chunkX = -RIVER_SCAN_EXTENT; chunkX <= RIVER_SCAN_EXTENT; chunkX += RIVER_SCAN_STEP) {
            for (int chunkZ = -RIVER_SCAN_EXTENT; chunkZ <= RIVER_SCAN_EXTENT; chunkZ += RIVER_SCAN_STEP) {
                if (isShallowRiverChunk(dimensionService, chunkX, chunkZ)) {
                    return new int[] {chunkX - windowRadius, chunkZ - windowRadius};
                }
            }
        }
        throw new IllegalStateException("no shallow river next to dry land found in the sampled area");
    }

    private static boolean isShallowRiverChunk(DimensionServiceImpl dimensionService,
            int chunkX, int chunkZ) {
        DimensionServiceImpl.ChunkTerrainResult chunk =
                dimensionService.computeChunkTerrain(GRAND_WILDS, chunkX, chunkZ);
        boolean hasWater = false;
        boolean hasLand = false;
        boolean hasDeepWater = false;
        for (int height : chunk.heights()) {
            if (height < SEA_LEVEL && height > SHALLOW_WATER_MAX) {
                hasWater = true;
            }
            if (height >= SEA_LEVEL) {
                hasLand = true;
            }
            if (height <= SHALLOW_WATER_MAX) {
                hasDeepWater = true;
            }
        }
        // Skip coastal chunks: an ocean bank is not a river, and its cliff profile
        // would fail the river assertions for the wrong reason.
        return hasWater && hasLand && !hasDeepWater;
    }
}
