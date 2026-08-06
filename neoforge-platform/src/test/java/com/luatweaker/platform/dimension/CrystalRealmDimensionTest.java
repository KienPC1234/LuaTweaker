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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loads the real arcane_rpg mod (Crystal Realm dimension) through the engine
 * and verifies the full pipeline: config parsing, terrain/biome callbacks,
 * portal mapping and virtual datapack biomes.
 */
public class CrystalRealmDimensionTest {

    private static final String DIMENSION_ID = "luatweaker:crystal_realm";

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
            @Override public String getPlatformName() { return "IntegrationTest"; }
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
    }

    @AfterAll
    public static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    public void crystalRealm_LoadsThroughRealEngine() {
        File baseDir = findLuamodsDir();
        assertTrue(baseDir.exists(), "luamods directory should exist at " + baseDir.getAbsolutePath());

        ILuaEngine engine = new CobaltLuaEngine(true);
        engine.setLuaDirectory(baseDir);

        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();

        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService,
                new NeoForgeRecipeManager());

        assertDoesNotThrow(() -> LuaModManager.loadLuaMods(baseDir, engine, "universal"),
                "Failed executing autonomous LuaMods (incl. arcane_rpg Crystal Realm)");

        Object serviceObj = LuaServiceRegistry.get("DimensionServiceImpl");
        assertTrue(serviceObj instanceof DimensionServiceImpl, "DimensionServiceImpl must be registered");
        DimensionServiceImpl service = (DimensionServiceImpl) serviceObj;

        // 1. Dimension config parsed from Lua + config file
        DimensionConfig config = service.getConfig(DIMENSION_ID);
        assertNotNull(config, "crystal_realm must be registered");
        assertEquals("luatweaker:crystal_grass", config.surfaceBlock());
        assertEquals("luatweaker:crystal_dirt", config.subsurfaceBlock());
        assertEquals("luatweaker:crystal_stone", config.fillerBlock());
        assertEquals("minecraft:water", config.waterBlock());
        assertTrue(config.hasBedrock(), "has_bedrock must come from the config file");
        assertEquals(8, config.biomeSize(), "biome_size must come from the config file");
        assertEquals(Integer.valueOf(0), config.spawnX(), "spawn_x must come from the config file");
        assertEquals(Integer.valueOf(0), config.spawnZ(), "spawn_z must come from the config file");
        assertTrue(config.hasSkyLight());
        assertEquals(3, config.biomes().size(), "3 biomes expected");
        assertEquals(5, config.spawnEntities().size(), "5 vanilla spawner entries expected");
        assertTrue(DimensionConfig.isColor(config.skyColor()));

        // 1b. Custom spawn point applied
        DimensionServiceImpl.SpawnPoint spawnPoint = service.getSpawnPoint(DIMENSION_ID);
        assertNotNull(spawnPoint, "spawn point must be set from config");
        assertEquals(0, spawnPoint.x());
        assertEquals(0, spawnPoint.z());

        // 2. Terrain generator produces sane heights + known blocks
        assertNotNull(service.getTerrainGenerator(DIMENSION_ID), "terrain generator must be set");
        Set<String> knownBlocks = Set.of("luatweaker:crystal_stone", "luatweaker:crystal_dirt", "luatweaker:crystal_peak");
        for (int i = 0; i < 64; i++) {
            DimensionServiceImpl.TerrainResult terrain =
                    service.computeTerrain(DIMENSION_ID, i * 7, i * 13, config.seaLevel());
            assertTrue(terrain.height() >= config.minHeight() + 1 && terrain.height() < config.maxHeight(),
                    "height out of range: " + terrain.height());
            assertTrue(knownBlocks.contains(terrain.blockId()),
                    "unexpected block: " + terrain.blockId());
        }

        // 3. Terrain is not a flat constant plane (noise must vary)
        int h1 = service.computeTerrain(DIMENSION_ID, 0, 0, config.seaLevel()).height();
        int h2 = service.computeTerrain(DIMENSION_ID, 1000, 1000, config.seaLevel()).height();
        assertNotEquals(h1, h2, "terrain must vary across the map");

        // 4. Biome provider returns only configured biome ids
        assertNotNull(service.getBiomeProvider(DIMENSION_ID), "biome provider must be set");
        Set<String> biomeIds = new HashSet<>();
        for (DimensionConfig.BiomeEntry biome : config.biomes()) biomeIds.add(biome.id());
        for (int i = 0; i < 32; i++) {
            String biome = service.computeBiome(DIMENSION_ID, i * 31, i * 17);
            assertNotNull(biome, "biome provider must return a biome id");
            assertTrue(biomeIds.contains(biome), "unexpected biome id: " + biome);
        }

        // 5. Portal mapping registered + target getter
        assertEquals(DIMENSION_ID, service.getPortals().get("luatweaker:crystal_portal"));
        assertEquals(DIMENSION_ID, service.getPortalTarget("luatweaker:crystal_portal"));

        // 5b. Block picker produces only valid in-range overrides
        assertNotNull(service.getBlockPicker(DIMENSION_ID), "block picker must be set");
        Set<String> validBlockIds = Set.of("minecraft:air", "minecraft:lava",
                "luatweaker:crystal_ore", "luatweaker:crystal_grass",
                "luatweaker:crystal_dirt", "luatweaker:crystal_stone", "luatweaker:crystal_peak");
        boolean foundOverride = false;
        for (int i = 0; i < 128; i++) {
            Map<Integer, String> overrides = service.computeBlockOverrides(
                    DIMENSION_ID, i * 3, i * 5, config.seaLevel() + 10, config.minHeight(), config.maxHeight());
            for (Map.Entry<Integer, String> entry : overrides.entrySet()) {
                foundOverride = true;
                assertTrue(entry.getKey() >= config.minHeight() && entry.getKey() < config.maxHeight(),
                        "override Y out of range: " + entry.getKey());
                assertTrue(validBlockIds.contains(entry.getValue()),
                        "unexpected override block: " + entry.getValue());
            }
        }
        assertTrue(foundOverride, "the picker must produce overrides somewhere");

        // 5c. SpawnRules module: full-control handlers only (no rule-based API)
        Object spawnObj = LuaServiceRegistry.get("SpawnRuleServiceImpl");
        assertTrue(spawnObj instanceof com.luatweaker.spawn.SpawnRuleServiceImpl,
                "SpawnRuleServiceImpl must be registered");
        com.luatweaker.spawn.SpawnRuleServiceImpl spawnRules =
                (com.luatweaker.spawn.SpawnRuleServiceImpl) spawnObj;
        assertNotNull(spawnRules.getHandler("luatweaker:crystal_realm"),
                "realm spawn handler must be registered");
        assertNotNull(spawnRules.getHandler("luatweaker:crystal_skylands"),
                "skylands spawn handler must be registered");

        // 5e. Biomes module: entities merged into biome JSONs
        Object biomesObj = LuaServiceRegistry.get("BiomesServiceImpl");
        assertTrue(biomesObj instanceof com.luatweaker.dimension.BiomesServiceImpl,
                "BiomesServiceImpl must be registered");
        String plainsBiome = datapackService.getVirtualFiles().get("data/luatweaker/worldgen/biome/crystal_plains.json");
        assertNotNull(plainsBiome, "crystal_plains biome JSON must exist");
        assertTrue(plainsBiome.contains("luatweaker:crystal_golem"),
                "crystal golem must be added to the crystal_plains biome spawners");
        assertTrue(plainsBiome.contains("\"monster\""),
                "golem must be in the monster category");

        // 6. Virtual datapack contains the biome JSONs
        assertTrue(datapackService.getVirtualFiles().containsKey("data/luatweaker/worldgen/biome/crystal_plains.json"),
                "crystal_plains biome must be materialized");
        assertTrue(datapackService.getVirtualFiles().containsKey("data/luatweaker/worldgen/biome/crystal_forest.json"),
                "crystal_forest biome must be materialized");
        assertTrue(datapackService.getVirtualFiles().containsKey("data/luatweaker/worldgen/biome/void_wastes.json"),
                "void_wastes biome must be materialized");

        // 7. Dimension blocks registered through Content
        Set<String> registeredBlocks = new HashSet<>();
        contentService.getRegisteredBlocks().forEach(builder -> registeredBlocks.add(builder.getId()));
        assertTrue(registeredBlocks.containsAll(Set.of("crystal_stone", "crystal_grass", "crystal_dirt",
                        "crystal_peak", "crystal_log", "crystal_leaves",
                        "luatweaker:crystal_portal", "luatweaker:crystal_sky_portal")),
                "missing dimension blocks in: " + registeredBlocks);

        // 8. NBT structure templates present for World:PlaceStructure
        File monolithNbt = findModFile(baseDir, "data/arcane_rpg/structures/sky_monolith.nbt");
        assertTrue(monolithNbt.exists(), "sky_monolith.nbt must exist for World:PlaceStructure");
        File outpostNbt = findModFile(baseDir, "data/arcane_rpg/structures/crystal_outpost.nbt");
        assertTrue(outpostNbt.exists(), "crystal_outpost.nbt must exist for World:PlaceStructure");
        assertTrue(outpostNbt.length() > 100, "crystal_outpost.nbt must not be empty");
    }

    @Test
    public void crystalSkylands_LoadsWithFloatingIslands() {
        File baseDir = findLuamodsDir();
        ILuaEngine engine = new CobaltLuaEngine(true);
        engine.setLuaDirectory(baseDir);

        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();

        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService,
                new NeoForgeRecipeManager());
        assertDoesNotThrow(() -> LuaModManager.loadLuaMods(baseDir, engine, "universal"));

        DimensionServiceImpl service = (DimensionServiceImpl) LuaServiceRegistry.get("DimensionServiceImpl");

        DimensionConfig skylands = service.getConfig("luatweaker:crystal_skylands");
        assertNotNull(skylands, "skylands dimension must be registered");
        assertNotNull(service.getTerrainGenerator("luatweaker:crystal_skylands"),
                "skylands terrain generator must be set (islands shaped in Lua)");
        assertNotNull(service.getBlockPicker("luatweaker:crystal_skylands"),
                "skylands block picker must be set (void carved in Lua)");

        // Islands at high altitude + void columns between them (all Lua logic).
        boolean island = false;
        boolean voidCol = false;
        boolean thinIsland = false;
        for (int i = 0; i < 300; i++) {
            DimensionServiceImpl.SurfaceResult r =
                    service.computeSurface("luatweaker:crystal_skylands", i * 5, i * 9, 0);
            int height = r.height();
            if (height <= skylands.minHeight() + 1) {
                voidCol = true;
            } else {
                island = true;
                assertTrue(height >= 180 && height <= 269, "island top out of range: " + height);
                // Island slab thickness comes from the Lua picker (air below the slab).
                Map<Integer, String> overrides = service.computeBlockOverrides(
                        "luatweaker:crystal_skylands", i * 5, i * 9, height, skylands.minHeight(), 320);
                int lowestAir = Integer.MAX_VALUE;
                int topAir = Integer.MIN_VALUE;
                for (Map.Entry<Integer, String> entry : overrides.entrySet()) {
                    if ("minecraft:air".equals(entry.getValue())) {
                        lowestAir = Math.min(lowestAir, entry.getKey());
                        topAir = Math.max(topAir, entry.getKey());
                    }
                }
                if (topAir > Integer.MIN_VALUE) {
                    thinIsland = true;
                    assertTrue(topAir >= height - 20, "island slab must be thin: top=" + height
                            + " airUpTo=" + topAir);
                    assertTrue(lowestAir <= skylands.minHeight() + 2,
                            "void must extend to the world bottom");
                }
            }
        }
        assertTrue(island, "skylands must produce islands");
        assertTrue(voidCol, "skylands must produce void between islands");
        assertTrue(thinIsland, "island slabs must float high above the world bottom");

        // Spawn point + portal.
        assertEquals(0, service.getSpawnPoint("luatweaker:crystal_skylands").x());
        assertEquals("luatweaker:crystal_skylands",
                service.getPortalTarget("luatweaker:crystal_sky_portal"));
        assertTrue(service.getDimensionIds().contains("luatweaker:crystal_skylands"),
                "both dimensions must be registered");
    }

    private File findModFile(File baseDir, String relativeSuffix) {
        return com.luatweaker.platform.TestPaths.findModFile(baseDir, relativeSuffix);
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

