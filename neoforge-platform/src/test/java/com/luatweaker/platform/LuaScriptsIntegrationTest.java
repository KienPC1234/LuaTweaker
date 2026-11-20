package com.luatweaker.platform;

import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.pal.IPlatformContent;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.bootstrap.LuaServiceBootstrap;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LuaScriptsIntegrationTest {

    private NeoForgeRecipeManager recipeManager;
    private static java.util.Map<String, Object> lastWrittenNbt;

    @BeforeEach
    public void setUp() {
        recipeManager = new NeoForgeRecipeManager();
        LuaServiceRegistry.clear();
        com.luatweaker.command.CommandServiceImpl.clear();

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
            public void sendPayloadBytes(String u, String c, byte[] d) {}
            public void broadcastPayloadBytes(String c, byte[] d) {}
            public void sendPayloadBytesToServer(String c, byte[] d) {}
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
            public boolean setBlockEntityData(String d, int x, int y, int z, java.util.Map<String, Object> data) { lastWrittenNbt = data; return true; }
            public boolean ejectContainerItem(String d, int x, int y, int z, int slot, int count) { return false; }
            public long fillBlocks(String d, int x1, int y1, int z1, int x2, int y2, int z2, String b, java.util.Map<String, Object> p) { return 0; }
            public long replaceBlocks(String d, int x1, int y1, int z1, int x2, int y2, int z2, String f, String t) { return 0; }
            public boolean placeStructure(String d, String t, int x, int y, int z, int r) { return false; }
            public boolean executeCommand(String c) { return false; }
        });

        Platform.setEntity(new com.luatweaker.api.pal.IPlatformEntity() {
            public void addCustomGoal(com.luatweaker.api.entity.IEntity e, int p, com.luatweaker.api.vm.ILuaTable g, com.luatweaker.api.vm.ILuaEngine en, boolean i) {}
            public void removeCustomGoal(com.luatweaker.api.entity.IEntity e, com.luatweaker.api.vm.ILuaTable g) {}
            public void clearCustomGoals(com.luatweaker.api.entity.IEntity e) {}
            public void addMeleeAttackGoal(com.luatweaker.api.entity.IEntity e, int p, double s, boolean m) {}
            public void addHurtByTargetGoal(com.luatweaker.api.entity.IEntity e, int p) {}
            public void addNearestAttackableTargetGoal(com.luatweaker.api.entity.IEntity e, int p, String t) {}
            public void addFloatGoal(com.luatweaker.api.entity.IEntity e, int p) {}
            public void addWanderGoal(com.luatweaker.api.entity.IEntity e, int p, double s) {}
            public void addLookAtGoal(com.luatweaker.api.entity.IEntity e, int p, double d, String t) {}
            public void addLookAroundGoal(com.luatweaker.api.entity.IEntity e, int p) {}
            public void addPanicGoal(com.luatweaker.api.entity.IEntity e, int p, double s) {}
            public void addAvoidGoal(com.luatweaker.api.entity.IEntity e, int p, String t, double d, double w, double s) {}
            public void addLeapGoal(com.luatweaker.api.entity.IEntity e, int p, double s) {}
            public void addBreakDoorGoal(com.luatweaker.api.entity.IEntity e, int p, boolean h) {}
            public void addTemptGoal(com.luatweaker.api.entity.IEntity e, int p, double s, String item) {}
            public void addMoveToTargetGoal(com.luatweaker.api.entity.IEntity e, int p, double s, double m) {}
            public java.util.List<java.util.Map<String, Object>> listGoals(com.luatweaker.api.entity.IEntity e) { return java.util.List.of(); }
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

        ILuaEngine engine = LuaEngineFactory.create(true);
        engine.setLuaDirectory(baseDir);

        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();

        // Register all services using LuaServiceBootstrap
        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService, recipeManager);

        LuaModManager.loadLuaMods(baseDir, engine, "universal");
        assertTrue(LuaModManager.getLoadErrors().isEmpty(),
                "Failed executing autonomous LuaMods in " + baseDir.getAbsolutePath()
                        + ": " + LuaModManager.getLoadErrors());

        // Showcase worlds must register through their real pipelines (chunk batch, height spec,
        // density spec) or the test scripts are silently not exercising the new APIs.
        Object dimensionService = LuaServiceRegistry.get("DimensionServiceImpl");
        assertTrue(dimensionService instanceof com.luatweaker.dimension.DimensionServiceImpl,
                "DimensionServiceImpl should be registered after loading the test mod");
        com.luatweaker.dimension.DimensionServiceImpl dimensions =
                (com.luatweaker.dimension.DimensionServiceImpl) dimensionService;
        assertTrue(dimensions.getDimensionIds().containsAll(java.util.List.of(
                        "luatweaker:grand_wilds", "luatweaker:alpine_spec", "luatweaker:sky_isles")),
                "vanilla showcase worlds missing: " + dimensions.getDimensionIds());
        assertEquals(com.luatweaker.dimension.DimensionServiceImpl.TerrainMode.CHUNK,
                dimensions.getTerrainMode("luatweaker:grand_wilds"));
        assertTrue(dimensions.hasBiomeBatch("luatweaker:grand_wilds"));
        assertEquals(com.luatweaker.dimension.DimensionServiceImpl.TerrainMode.SPEC,
                dimensions.getTerrainMode("luatweaker:alpine_spec"));
        assertNotNull(dimensions.getTerrainSpec("luatweaker:alpine_spec"));
        assertNotNull(dimensions.getTerrainSpec("luatweaker:sky_isles").density(),
                "sky_isles must use the 3D density pipeline");
        assertFalse(dimensions.getTerrainSpec("luatweaker:sky_isles").water(),
                "sky_isles must not fill water below the islands");

        // The callbacks themselves must produce varied terrain (a flat world here means the
        // chunk cache or a script edit collapsed the noise stack).
        int wildsMin = Integer.MAX_VALUE;
        int wildsMax = Integer.MIN_VALUE;
        int wildsColumns = 0;
        int wildsOcean = 0;
        int wildsPeaks = 0;
        java.util.List<Integer> wildsSamples = new java.util.ArrayList<>();
        for (int chunkX = -32; chunkX <= 32; chunkX += 8) {
            for (int chunkZ = -32; chunkZ <= 32; chunkZ += 8) {
                com.luatweaker.dimension.DimensionServiceImpl.ChunkTerrainResult chunk =
                        dimensions.computeChunkTerrain("luatweaker:grand_wilds", chunkX, chunkZ);
                for (int height : chunk.heights()) {
                    wildsMin = Math.min(wildsMin, height);
                    wildsMax = Math.max(wildsMax, height);
                    if (height < 62) wildsOcean++;
                    if (height > 122) wildsPeaks++;
                    wildsSamples.add(height);
                    wildsColumns++;
                }
            }
        }
        double oceanFraction = (double) wildsOcean / wildsColumns;
        double peakFraction = (double) wildsPeaks / wildsColumns;
        java.util.Collections.sort(wildsSamples);
        int medianHeight = wildsSamples.get(wildsSamples.size() / 2);
        assertTrue(wildsMax - wildsMin > 50,
                "grand_wilds terrain is flat: " + wildsMin + ".." + wildsMax);
        assertTrue(medianHeight > 80 && medianHeight < 170,
                "grand_wilds median height is not land: " + medianHeight);
        assertTrue(oceanFraction > 0.03 && oceanFraction < 0.40,
                "grand_wilds ocean/river fraction out of range: " + oceanFraction);
        assertTrue(peakFraction > 0.03,
                "grand_wilds has no high mountains: " + peakFraction);

        com.luatweaker.dimension.TerrainSampler skySampler =
                dimensions.createTerrainSampler("luatweaker:sky_isles");
        assertNotNull(skySampler);
        int minTop = Integer.MAX_VALUE;
        int maxTop = Integer.MIN_VALUE;
        int voidColumns = 0;
        int skyColumns = 0;
        for (int chunkX = 8; chunkX <= 10; chunkX++) {
            for (int chunkZ = 8; chunkZ <= 10; chunkZ++) {
                com.luatweaker.dimension.DensityField field =
                        skySampler.sampleDensity(chunkX, chunkZ, -64, 320);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int top = field.topSolid(x, z);
                        if (top < -64) {
                            voidColumns++;
                        } else {
                            minTop = Math.min(minTop, top);
                            maxTop = Math.max(maxTop, top);
                        }
                        skyColumns++;
                    }
                }
            }
        }
        double voidFraction = (double) voidColumns / skyColumns;
        assertTrue(maxTop - minTop > 10,
                "sky_isles density is flat: " + minTop + ".." + maxTop);
        assertTrue(voidFraction > 0.45 && voidFraction < 0.92,
                "sky_isles is not an island field, void fraction=" + voidFraction);

        // River/lake quality: a wide, shallow valley must not be a one-block chasm. The old
        // lake carve produced 130-block drops next to a single water column.
        int riverChunks = 9;
        int riverBase = -4;
        int riverSize = riverChunks * 16;
        int[][] riverHeights = new int[riverSize][riverSize];
        for (int cx = 0; cx < riverChunks; cx++) {
            for (int cz = 0; cz < riverChunks; cz++) {
                com.luatweaker.dimension.DimensionServiceImpl.ChunkTerrainResult chunk =
                        dimensions.computeChunkTerrain("luatweaker:grand_wilds",
                                riverBase + cx, riverBase + cz);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        riverHeights[cz * 16 + z][cx * 16 + x] = chunk.heights()[z * 16 + x];
                    }
                }
            }
        }
        java.util.List<Integer> bankDrops = new java.util.ArrayList<>();
        java.util.List<Integer> riverWidths = new java.util.ArrayList<>();
        int waterColumns = 0;
        for (int z = 0; z < riverSize; z++) {
            int x = 0;
            while (x < riverSize) {
                if (riverHeights[z][x] >= 62) {
                    x++;
                    continue;
                }
                int start = x;
                int bed = riverHeights[z][x];
                while (x < riverSize && riverHeights[z][x] < 62) {
                    bed = Math.min(bed, riverHeights[z][x]);
                    waterColumns++;
                    x++;
                }
                int end = x - 1;
                if (start == 0 || end == riverSize - 1) continue;
                int width = end - start + 1;
                if (width > 48) continue; // ocean / large lake
                int bank = Math.min(riverHeights[z][start - 1], riverHeights[z][end + 1]) - bed;
                bankDrops.add(bank);
                riverWidths.add(width);
            }
        }
        java.util.Collections.sort(bankDrops);
        java.util.Collections.sort(riverWidths);
        int maxBankDrop = bankDrops.isEmpty() ? 0 : bankDrops.get(bankDrops.size() - 1);
        int p95BankDrop = bankDrops.isEmpty() ? 0 : bankDrops.get((int) (bankDrops.size() * 0.95));
        int medianRiverWidth = riverWidths.isEmpty() ? 0 : riverWidths.get(riverWidths.size() / 2);
        int p95RiverWidth = riverWidths.isEmpty() ? 0 : riverWidths.get((int) (riverWidths.size() * 0.95));
        int p50BankDrop = bankDrops.isEmpty() ? 0 : bankDrops.get(bankDrops.size() / 2);
        long narrowRuns = riverWidths.stream().filter(w -> w <= 2).count();
        double riverWaterFraction = (double) waterColumns / (riverSize * riverSize);
        assertTrue(bankDrops.size() > 10, "no rivers found in the sample");
        assertTrue(riverWaterFraction > 0.01 && riverWaterFraction < 0.30,
                "river/lake water fraction out of range: " + riverWaterFraction);
        assertTrue(maxBankDrop <= 45,
                "river/lake bank drop too deep: " + maxBankDrop + " blocks (p95=" + p95BankDrop + ")");
        assertTrue(p50BankDrop <= 10 && p95BankDrop <= 20,
                "river banks are cliffs: p50=" + p50BankDrop + " p95=" + p95BankDrop);
        assertTrue(medianRiverWidth >= 2,
                "rivers are one-block cuts, median width=" + medianRiverWidth);
        assertTrue(p95RiverWidth >= 4,
                "rivers are too narrow, p95 width=" + p95RiverWidth);
        assertTrue(narrowRuns < riverWidths.size() / 4,
                "too many one-block rivers: " + narrowRuns + "/" + riverWidths.size());

        // Verify Network RemoteEvent Firing & Execution
        Object netService = LuaServiceRegistry.get("NetworkServiceImpl");
        assertTrue(netService instanceof com.luatweaker.network.NetworkServiceImpl, "NetworkServiceImpl should be registered");

        com.luatweaker.network.NetworkServiceImpl ns = (com.luatweaker.network.NetworkServiceImpl) netService;
        assertDoesNotThrow(() -> ns.OnClientFired("StaffSwapSkill", "dummy-uuid", new byte[0]),
                "StaffSwapSkill packet firing failed");
        assertDoesNotThrow(() -> ns.OnClientFired("StaffCastSkill", "dummy-uuid", new byte[0]),
                "StaffCastSkill packet firing failed");
    }

    @Test
    public void testBlockStateAndNbtBindings() {
        File baseDir = findLuamodsDir();
        ILuaEngine engine = LuaEngineFactory.create(true);
        engine.setLuaDirectory(baseDir);
        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();
        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService, datapackService, recipeManager);

        // No server is running in tests: block-state/NBT calls must return
        // nil/false gracefully and never throw.
        engine.executeStringOrThrow(
                "local World = require('LuaTweaker.World')\n" +
                "assert(World:GetBlockState(0, 0, 0) == nil, 'GetBlockState should be nil without server')\n" +
                "assert(World:GetBlockEntityData(0, 0, 0) == nil, 'GetBlockEntityData should be nil without server')\n" +
                "assert(World:SetBlockState(0, 0, 0, 'minecraft:stone') == false, 'SetBlockState should fail without server')\n" +
                "assert(World:SetBlockEntityData(0, 0, 0, {Items = {{id='minecraft:stick', count=1, slot=0}}}) == true, 'SetBlockEntityData should accept valid data')\n" +
                "assert(World:EjectContainerItem(0, 0, 0, 0, 1) == false, 'EjectContainerItem should fail without server')\n" +
                "assert(World:ExecuteCommand('say hi') == false, 'ExecuteCommand should fail without server')\n",
                "testBlockStateAndNbtBindings");

        // Lua array-like tables (Items list) must arrive as Java Lists, not Maps —
        // otherwise NbtCodec would write a CompoundTag instead of a ListTag and
        // ContainerHelper.loadAllItems would fail, wiping the crate contents.
        assertTrue(lastWrittenNbt != null && lastWrittenNbt.get("Items") instanceof java.util.List,
                "Lua array table must convert to a Java List for NBT lists");
        java.util.List<?> items = (java.util.List<?>) lastWrittenNbt.get("Items");
        org.junit.jupiter.api.Assertions.assertEquals(1, items.size());
        Object first = items.get(0);
        assertTrue(first instanceof java.util.Map, "nested item entry should be a Map");
        org.junit.jupiter.api.Assertions.assertEquals("minecraft:stick",
                ((java.util.Map<?, ?>) first).get("id"));
    }

    private File findLuamodsDir() {
        return com.luatweaker.platform.TestPaths.findLuamodsDir();
    }
}
