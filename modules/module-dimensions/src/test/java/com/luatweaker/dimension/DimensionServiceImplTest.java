package com.luatweaker.dimension;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.pal.IPlatformContent;
import com.luatweaker.api.pal.IPlatformDimension;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class DimensionServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine engine;
    private DimensionServiceImpl service;

    @BeforeEach
    void setup() {
        engine = new CobaltLuaEngine();
        service = new DimensionServiceImpl(engine);
    }

    // ---------- create ----------

    @Test
    void create_ParsesFullConfig() {
        ILuaTable config = engine.createTable();
        config.rawset("hasSkyLight", false);
        config.rawset("skyColor", 0xFF88FF);
        config.rawset("ambientLight", 0.25);
        config.rawset("seaLevel", 32);
        config.rawset("minHeight", -64);
        config.rawset("maxHeight", 256);
        config.rawset("surfaceBlock", "arcane:crystal_grass");
        config.rawset("subsurfaceBlock", "arcane:crystal_dirt");
        config.rawset("fillerBlock", "minecraft:stone");
        config.rawset("fixedTime", 6000.0);

        ILuaTable biome1 = engine.createTable();
        biome1.rawset("id", "arcane:crystal_plains");
        biome1.rawset("weight", 5);
        ILuaTable biome2 = engine.createTable();
        biome2.rawset("id", "minecraft:plains");
        ILuaTable biomes = engine.createTable();
        biomes.rawset(1, biome1);
        biomes.rawset(2, biome2);
        config.rawset("biomes", biomes);

        ILuaTable spawn = engine.createTable();
        spawn.rawset("entity", "arcane:crystal_elemental");
        spawn.rawset("weight", 10);
        spawn.rawset("minGroup", 1);
        spawn.rawset("maxGroup", 3);
        ILuaTable spawns = engine.createTable();
        spawns.rawset(1, spawn);
        config.rawset("spawnEntities", spawns);

        service.create("arcane:crystal_realm", config);

        DimensionConfig cfg = service.getConfig("arcane:crystal_realm");
        assertNotNull(cfg);
        assertEquals(false, cfg.hasSkyLight());
        assertEquals(0xFF88FF, cfg.skyColor());
        assertEquals(0.25, cfg.ambientLight(), 1e-9);
        assertEquals(32, cfg.seaLevel());
        assertEquals(-64, cfg.minHeight());
        assertEquals(256, cfg.maxHeight());
        assertEquals(320, cfg.logicalHeight(), "logicalHeight must default to maxHeight - minHeight");
        assertEquals(6000L, cfg.fixedTime());
        assertEquals("arcane:crystal_grass", cfg.surfaceBlock());
        assertEquals(2, cfg.biomes().size());
        assertEquals(5, cfg.biomes().get(0).weight());
        assertEquals(1, cfg.biomes().get(1).weight(), "weight must default to 1");
        assertEquals(1, cfg.spawnEntities().size());
        assertEquals(3, cfg.spawnEntities().get(0).maxGroup());
    }

    @Test
    void create_UsesDefaultsForMissingFields() {
        ILuaTable config = engine.createTable();
        service.create("minecraft:test_dim", config);

        DimensionConfig cfg = service.getConfig("minecraft:test_dim");
        assertNotNull(cfg);
        assertEquals(true, cfg.hasSkyLight());
        assertEquals(63, cfg.seaLevel());
        assertEquals(-64, cfg.minHeight());
        assertEquals(320, cfg.maxHeight());
        assertEquals("minecraft:grass_block", cfg.surfaceBlock());
        assertEquals("minecraft:stone", cfg.fillerBlock());
        assertEquals(0x78A7FF, cfg.skyColor());
        assertTrue(cfg.biomes().isEmpty());
        assertTrue(cfg.spawnEntities().isEmpty());
        assertNull(cfg.fixedTime());
    }

    @Test
    void create_RejectsInvalidDimensionId() {
        ILuaTable config = engine.createTable();
        assertThrows(IllegalArgumentException.class, () -> service.create("", config));
        assertThrows(IllegalArgumentException.class, () -> service.create("UPPER:case", config));
        assertThrows(IllegalArgumentException.class, () -> service.create("bad id!", config));
    }

    @Test
    void create_RejectsNonTableConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("minecraft:test", engine.wrapNumber(42)));
    }

    @Test
    void create_RejectsInvalidColor() {
        ILuaTable config = engine.createTable();
        config.rawset("skyColor", 0xFFFFFFF);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    @Test
    void create_RejectsInvalidHeightRange() {
        ILuaTable config = engine.createTable();
        config.rawset("minHeight", 100);
        config.rawset("maxHeight", 50);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    @Test
    void create_RejectsUnsupportedTerrainType() {
        ILuaTable config = engine.createTable();
        config.rawset("terrain", "vanilla");
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    @Test
    void create_RejectsInvalidBiomeEntry() {
        ILuaTable config = engine.createTable();
        ILuaTable biome = engine.createTable();
        biome.rawset("weight", 5); // missing id
        ILuaTable biomes = engine.createTable();
        biomes.rawset(1, biome);
        config.rawset("biomes", biomes);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    @Test
    void create_RejectsNegativeAmbientLight() {
        ILuaTable config = engine.createTable();
        config.rawset("ambientLight", -0.5);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    @Test
    void create_RejectsInvalidSurfaceBlock() {
        ILuaTable config = engine.createTable();
        config.rawset("surfaceBlock", "not a valid id");
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    @Test
    void create_TwiceOverwritesConfig() {
        ILuaTable configA = engine.createTable();
        configA.rawset("seaLevel", 10);
        ILuaTable configB = engine.createTable();
        configB.rawset("seaLevel", 90);
        service.create("minecraft:test", configA);
        service.create("minecraft:test", configB);
        assertEquals(90, service.getConfig("minecraft:test").seaLevel());
        assertEquals(1, service.getDimensionIds().size());
    }

    // ---------- terrain generator ----------

    @Test
    void setTerrainGenerator_StoresFunction() {
        engine.executeString(
                "__gen = function(x, z, baseHeight) return 80 end",
                "test_set_gen");

        service.create("minecraft:test", engine.createTable());
        service.setTerrainGenerator("minecraft:test", engine.getGlobalEnvironment().rawget("__gen"));

        DimensionServiceImpl.TerrainResult result =
                service.computeTerrain("minecraft:test", 10, 20, 63);
        assertEquals(80, result.height());
        assertEquals("minecraft:grass_block", result.blockId(), "block id must default to surface block");
    }

    @Test
    void setTerrainGenerator_RejectsNonFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setTerrainGenerator("minecraft:test", engine.wrapNumber(5)));
        assertThrows(IllegalArgumentException.class,
                () -> service.setTerrainGenerator("minecraft:test", "not a function"));
    }

    @Test
    void setTerrainGenerator_RejectsInvalidDimensionId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setTerrainGenerator("", engine.createTable()));
    }

    @Test
    void computeTerrain_ReturnsBlockIdFromLua() {
        engine.executeString(
                "__gen2 = function(x, z, baseHeight) return 120, 'arcane:crystal_peak' end",
                "test_set_gen2");
        service.create("minecraft:test", engine.createTable());
        service.setTerrainGenerator("minecraft:test", engine.getGlobalEnvironment().rawget("__gen2"));

        DimensionServiceImpl.TerrainResult result = service.computeTerrain("minecraft:test", 0, 0, 63);
        assertEquals(120, result.height());
        assertEquals("arcane:crystal_peak", result.blockId());
    }

    @Test
    void computeTerrain_FallsBackToBaseHeightWithoutGenerator() {
        service.create("minecraft:test", engine.createTable());
        DimensionServiceImpl.TerrainResult result = service.computeTerrain("minecraft:test", 5, 5, 70);
        assertEquals(70, result.height());
        assertEquals("minecraft:grass_block", result.blockId());
    }

    @Test
    void computeTerrain_RejectsUnknownDimension() {
        assertThrows(IllegalArgumentException.class, () -> service.computeTerrain("minecraft:nope", 0, 0, 63));
    }

    @Test
    void computeTerrain_IgnoresInvalidLuaHeightAndFallsBack() {
        engine.executeString("__bad = function(x, z, h) end", "test_bad_gen");
        service.create("minecraft:test", engine.createTable());
        service.setTerrainGenerator("minecraft:test", engine.getGlobalEnvironment().rawget("__bad"));
        DimensionServiceImpl.TerrainResult result = service.computeTerrain("minecraft:test", 1, 1, 55);
        assertEquals(55, result.height(), "invalid generator output must fall back to base height");
    }

    @Test
    void generationVersion_BumpsOnChange() {
        engine.executeString("__gen3 = function(x, z, h) return 10 end", "test_ver");
        service.create("minecraft:test", engine.createTable());
        long v1 = service.getGenerationVersion();
        service.create("minecraft:test", engine.createTable());
        assertTrue(service.getGenerationVersion() > v1);
        service.setTerrainGenerator("minecraft:test", engine.getGlobalEnvironment().rawget("__gen3"));
        assertTrue(service.getGenerationVersion() > v1 + 1);
    }

    // ---------- biome provider ----------

    @Test
    void setBiomeProvider_StoresFunction() {
        engine.executeString(
                "__biome = function(x, z) return 'arcane:crystal_plains' end",
                "test_set_biome");
        service.create("minecraft:test", engine.createTable());
        service.setBiomeProvider("minecraft:test", engine.getGlobalEnvironment().rawget("__biome"));
        assertNotNull(service.getBiomeProvider("minecraft:test"));
    }

    @Test
    void setBiomeProvider_RejectsNonFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setBiomeProvider("minecraft:test", "nope"));
    }

    // ---------- portals ----------

    @Test
    void registerPortal_StoresMapping() {
        service.registerPortal("arcane:crystal_portal", "arcane:crystal_realm");
        assertEquals("arcane:crystal_realm", service.getPortals().get("arcane:crystal_portal"));
    }

    @Test
    void registerPortal_RejectsInvalidIds() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerPortal("", "arcane:crystal_realm"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerPortal("arcane:crystal_portal", ""));
    }

    // ---------- getDimension info table ----------

    @Test
    void getDimension_ReturnsInfoTable() {
        ILuaTable config = engine.createTable();
        config.rawset("seaLevel", 40);
        config.rawset("surfaceBlock", "arcane:crystal_grass");
        config.rawset("waterBlock", "minecraft:lava");
        config.rawset("hasBedrock", true);
        config.rawset("biomeSize", 12);
        service.create("arcane:crystal_realm", config);
        service.registerPortal("arcane:crystal_portal", "arcane:crystal_realm");
        service.setSpawnPoint("arcane:crystal_realm", 7, 9);

        ILuaValue info = (ILuaValue) service.getDimension("arcane:crystal_realm");
        assertNotNull(info);
        assertTrue(info.isTable());
        assertEquals("arcane:crystal_realm", info.asTable().rawget("id").asString());
        assertEquals(40, info.asTable().rawget("seaLevel").asInt());
        assertEquals("arcane:crystal_grass", info.asTable().rawget("surfaceBlock").asString());
        assertEquals("minecraft:lava", info.asTable().rawget("waterBlock").asString());
        assertTrue(info.asTable().rawget("hasBedrock").asBoolean());
        assertEquals(12, info.asTable().rawget("biomeSize").asInt());
        assertEquals(7, info.asTable().rawget("spawnX").asInt());
        assertEquals(9, info.asTable().rawget("spawnZ").asInt());
        assertFalse(info.asTable().rawget("hasBlockPicker").asBoolean(), "no picker set in this test");
        ILuaValue portals = info.asTable().rawget("portals");
        assertTrue(portals.isTable());
        assertEquals("arcane:crystal_realm", portals.asTable().rawget("arcane:crystal_portal").asString());
        assertTrue(info.asTable().rawget("biomes").isTable());
        assertTrue(info.asTable().rawget("spawnEntities").isTable());
    }

    @Test
    void getDimension_ReturnsNullForUnknown() {
        assertNull(service.getDimension("minecraft:unknown"));
    }

    @Test
    void getDimension_RejectsInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> service.getDimension("Bogus Id!"));
    }

    @Test
    void create_ParsesWaterBlockBiomeSizeBedrockAndSpawn() {
        ILuaTable config = engine.createTable();
        config.rawset("waterBlock", "minecraft:lava");
        config.rawset("hasBedrock", true);
        config.rawset("biomeSize", 16);
        config.rawset("spawnX", 100);
        config.rawset("spawnZ", -200);
        service.create("minecraft:test", config);

        DimensionConfig cfg = service.getConfig("minecraft:test");
        assertEquals("minecraft:lava", cfg.waterBlock());
        assertTrue(cfg.hasBedrock());
        assertEquals(16, cfg.biomeSize());
        assertEquals(100, cfg.spawnX());
        assertEquals(-200, cfg.spawnZ());
    }

    @Test
    void create_RejectsPartialSpawnPair() {
        ILuaTable config = engine.createTable();
        config.rawset("spawnX", 10);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    @Test
    void create_DefaultsNewFields() {
        service.create("minecraft:test", engine.createTable());
        DimensionConfig cfg = service.getConfig("minecraft:test");
        assertEquals("minecraft:water", cfg.waterBlock());
        assertFalse(cfg.hasBedrock());
        assertEquals(4, cfg.biomeSize());
        assertNull(cfg.spawnX());
        assertNull(cfg.spawnZ());
    }

    // ---------- block picker ----------

    @Test
    void setBlockPicker_StoresAndComputesOverrides() {
        engine.executeString(
                "picker = function(x, z, surfaceY, minY)\n" +
                "    local o = {}\n" +
                "    o[surfaceY - 5] = 'minecraft:air'\n" +
                "    o[surfaceY - 10] = 'luatweaker:crystal_ore'\n" +
                "    o[99999] = 'minecraft:stone'\n" +
                "    o['bad'] = 'minecraft:stone'\n" +
                "    return o\n" +
                "end",
                "test_picker");
        service.create("minecraft:test", engine.createTable());
        service.setBlockPicker("minecraft:test", engine.getGlobalEnvironment().rawget("picker"));

        Map<Integer, String> overrides = service.computeBlockOverrides("minecraft:test", 1, 2, 80, -64, 320);
        assertEquals(2, overrides.size(), "out-of-range and non-numeric entries must be dropped");
        assertEquals("minecraft:air", overrides.get(75));
        assertEquals("luatweaker:crystal_ore", overrides.get(70));
    }

    @Test
    void setBlockPicker_RejectsNonFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setBlockPicker("minecraft:test", engine.wrapNumber(5)));
    }

    @Test
    void computeBlockOverrides_EmptyWithoutPicker() {
        service.create("minecraft:test", engine.createTable());
        assertTrue(service.computeBlockOverrides("minecraft:test", 0, 0, 80, -64, 320).isEmpty());
    }

    @Test
    void computeBlockOverrides_IgnoresInvalidBlockIds() {
        engine.executeString(
                "badPicker = function(x, z, surfaceY, minY)\n" +
                "    local o = {}\n" +
                "    o[10] = 'not a block id'\n" +
                "    return o\n" +
                "end",
                "test_bad_picker");
        service.create("minecraft:test", engine.createTable());
        service.setBlockPicker("minecraft:test", engine.getGlobalEnvironment().rawget("badPicker"));
        assertTrue(service.computeBlockOverrides("minecraft:test", 0, 0, 80, -64, 320).isEmpty());
    }

    @Test
    void computeBlockOverrides_NilReturnMeansNoOverrides() {
        engine.executeString("nilPicker = function(x, z, surfaceY, minY) end", "test_nil_picker");
        service.create("minecraft:test", engine.createTable());
        service.setBlockPicker("minecraft:test", engine.getGlobalEnvironment().rawget("nilPicker"));
        assertTrue(service.computeBlockOverrides("minecraft:test", 0, 0, 80, -64, 320).isEmpty());
    }

    // ---------- spawn point ----------

    @Test
    void setSpawnPoint_OverridesConfig() {
        ILuaTable config = engine.createTable();
        config.rawset("spawnX", 100);
        config.rawset("spawnZ", 200);
        service.create("minecraft:test", config);
        assertEquals(100, service.getSpawnPoint("minecraft:test").x());

        service.setSpawnPoint("minecraft:test", 500, -500);
        DimensionServiceImpl.SpawnPoint point = service.getSpawnPoint("minecraft:test");
        assertEquals(500, point.x());
        assertEquals(-500, point.z());
    }

    @Test
    void getSpawnPoint_ReturnsNullWhenUnset() {
        service.create("minecraft:test", engine.createTable());
        assertNull(service.getSpawnPoint("minecraft:test"));
    }

    @Test
    void setSpawnPoint_RejectsInvalidDimensionId() {
        assertThrows(IllegalArgumentException.class, () -> service.setSpawnPoint("", 0, 0));
    }

    // ---------- portal target ----------

    @Test
    void getPortalTarget_ReturnsRegisteredTarget() {
        service.registerPortal("arcane:crystal_portal", "arcane:crystal_realm");
        assertEquals("arcane:crystal_realm", service.getPortalTarget("arcane:crystal_portal"));
        assertNull(service.getPortalTarget("arcane:unknown_block"));
    }

    @Test
    void getPortalTarget_RejectsInvalidBlockId() {
        assertThrows(IllegalArgumentException.class, () -> service.getPortalTarget("Bad Id!"));
    }

    // ---------- strata picker ----------

    @Test
    void setStrataPicker_StoresAndComputesOverrides() {
        engine.executeString(
                "strataPicker = function(x, z, surfaceY)\n" +
                "    local o = {}\n" +
                "    o[2] = 'minecraft:deepslate'\n" +
                "    o[5] = 'minecraft:netherrack'\n" +
                "    o[0] = 'minecraft:stone'\n" +
                "    o[99999] = 'minecraft:stone'\n" +
                "    return o\n" +
                "end",
                "test_strata_picker");
        service.create("minecraft:test", engine.createTable());
        service.setStrataPicker("minecraft:test", engine.getGlobalEnvironment().rawget("strataPicker"));

        Map<Integer, String> overrides = service.computeStrataOverrides("minecraft:test", 1, 2, 80);
        assertEquals(2, overrides.size(), "depth 0 and out-of-range entries must be dropped");
        assertEquals("minecraft:deepslate", overrides.get(2));
        assertEquals("minecraft:netherrack", overrides.get(5));
    }

    @Test
    void setStrataPicker_RejectsNonFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setStrataPicker("minecraft:test", "nope"));
    }

    @Test
    void computeStrataOverrides_EmptyWithoutPicker() {
        service.create("minecraft:test", engine.createTable());
        assertTrue(service.computeStrataOverrides("minecraft:test", 0, 0, 80).isEmpty());
    }

    @Test
    void create_ParsesAdvancedDimensionTypeFields() {
        ILuaTable config = engine.createTable();
        config.rawset("piglinSafe", true);
        config.rawset("hasRaids", true);
        config.rawset("monsterSpawnLightLevel", 5);
        config.rawset("monsterSpawnBlockLightLimit", 3);
        config.rawset("infiniburn", "#minecraft:infiniburn_nether");
        config.rawset("effectsLocation", "minecraft:the_nether");
        service.create("minecraft:test", config);

        DimensionConfig cfg = service.getConfig("minecraft:test");
        assertTrue(cfg.piglinSafe());
        assertTrue(cfg.hasRaids());
        assertEquals(5, cfg.monsterSpawnLightLevel());
        assertEquals(3, cfg.monsterSpawnBlockLightLimit());
        assertEquals("#minecraft:infiniburn_nether", cfg.infiniburn());
        assertEquals("minecraft:the_nether", cfg.effectsLocation());
    }

    @Test
    void create_RejectsInvalidCoordinateScale() {
        ILuaTable config = engine.createTable();
        config.rawset("coordinateScale", 0);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
        ILuaTable config2 = engine.createTable();
        config2.rawset("coordinateScale", -2.0);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config2));
    }

    @Test
    void create_RejectsSeaLevelOutsideHeightRange() {
        ILuaTable config = engine.createTable();
        config.rawset("minHeight", 0);
        config.rawset("maxHeight", 100);
        config.rawset("seaLevel", 100);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
        ILuaTable config2 = engine.createTable();
        config2.rawset("minHeight", 0);
        config2.rawset("maxHeight", 100);
        config2.rawset("seaLevel", -1);
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config2));
    }

    @Test
    void create_RejectsInvalidEffectsLocation() {
        ILuaTable config = engine.createTable();
        config.rawset("effectsLocation", "no-namespace");
        assertThrows(IllegalArgumentException.class, () -> service.create("minecraft:test", config));
    }

    // ---------- terrain column (classic stack) ----------

    @Test
    void blockAtDepth_FallsBackToClassicStack() {
        service.create("minecraft:test", engine.createTable());
        DimensionConfig cfg = service.getConfig("minecraft:test");
        assertEquals("minecraft:grass_block", TerrainColumn.blockAtDepth(cfg, 70, 70));
        assertEquals("minecraft:dirt", TerrainColumn.blockAtDepth(cfg, 70, 69));
        assertEquals("minecraft:stone", TerrainColumn.blockAtDepth(cfg, 70, 68));
    }

    @Test
    void blockAtDepth_PlacesBedrockAtBottom() {
        ILuaTable config = engine.createTable();
        config.rawset("hasBedrock", true);
        service.create("minecraft:test", config);
        DimensionConfig cfg = service.getConfig("minecraft:test");
        assertEquals("minecraft:bedrock", TerrainColumn.blockAtDepth(cfg, 63, cfg.minHeight()));
        assertNotEquals("minecraft:bedrock", TerrainColumn.blockAtDepth(cfg, 63, cfg.minHeight() + 1));
    }

    // ---------- teleport ----------

    @BeforeAll
    static void setUpPlatform() {
        Platform.setContent(new IPlatformContent() {
            @Override public com.luatweaker.api.objects.IItem createItem(String itemId, int count) { return null; }
            @Override public boolean itemExists(String itemId) { return false; }
            @Override public boolean blockExists(String blockId) { return false; }
            @Override public boolean fluidExists(String fluidId) { return false; }
            @Override public boolean tagExists(String tagId) { return false; }
            @Override public boolean isModLoaded(String modId) { return false; }
            @Override public boolean isClient() { return false; }
            @Override public boolean isDedicatedServer() { return false; }
            @Override public String getPlatformName() { return "test"; }
            @Override public java.util.Set<String> getSupportedMobParents() { return java.util.Set.of(); }
            @Override public List<com.luatweaker.api.objects.IRecipe> getAllRecipes() { return List.of(); }
        });
    }

    @Test
    void teleportTo_DelegatesToPlatform() {
        AtomicBoolean called = new AtomicBoolean(false);
        Platform.setDimension(new IPlatformDimension() {
            @Override public boolean teleportToDimension(Object rawPlayerEntity, String dimensionId) {
                called.set(true);
                assertEquals("minecraft:test", dimensionId);
                return true;
            }
            @Override public Object getLevel(String dimensionId) { return null; }
        });

        IEntity player = new IEntity() {
            @Override public String getType() { return "minecraft:player"; }
            @Override public String getName() { return "P"; }
            @Override public float getHealth() { return 20; }
            @Override public void setHealth(float health) {}
            @Override public float getMaxHealth() { return 20; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() {}
            @Override public Object getRawEntity() { return "mock-raw-player"; }
        };

        service.create("minecraft:test", engine.createTable());
        service.teleportTo(player, "minecraft:test");
        assertTrue(called.get());
    }

    @Test
    void teleportTo_RejectsUnknownDimension() {
        IEntity player = new IEntity() {
            @Override public String getType() { return "minecraft:player"; }
            @Override public String getName() { return "P"; }
            @Override public float getHealth() { return 20; }
            @Override public void setHealth(float health) {}
            @Override public float getMaxHealth() { return 20; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() {}
            @Override public Object getRawEntity() { return "mock"; }
        };
        assertThrows(IllegalArgumentException.class, () -> service.teleportTo(player, "minecraft:unknown"));
    }

    @Test
    void teleportTo_ThrowsWhenLevelNotLoaded() {
        Platform.setDimension(new IPlatformDimension() {
            @Override public boolean teleportToDimension(Object rawPlayerEntity, String dimensionId) { return false; }
            @Override public Object getLevel(String dimensionId) { return null; }
        });
        IEntity player = new IEntity() {
            @Override public String getType() { return "minecraft:player"; }
            @Override public String getName() { return "P"; }
            @Override public float getHealth() { return 20; }
            @Override public void setHealth(float health) {}
            @Override public float getMaxHealth() { return 20; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() {}
            @Override public Object getRawEntity() { return "mock"; }
        };
        service.create("minecraft:test", engine.createTable());
        assertThrows(IllegalStateException.class, () -> service.teleportTo(player, "minecraft:test"));
    }
}
