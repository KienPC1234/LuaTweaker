package com.luatweaker.platform.dimension;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.dimension.DimensionLuaBinding;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Lua-registered dimensions are materialized into valid
 * virtual datapack files (dimension type + dimension entry referencing the
 * luatweaker:lua generator codecs).
 */
public class NeoForgeDimensionProviderTest {

    private static class FakeDatapack implements IDatapackService {
        final Map<String, String> files = new ConcurrentHashMap<>();

        @Override public void addJsonRecipe(String recipeId, String jsonContent) { files.put("recipe/" + recipeId, jsonContent); }
        @Override public void addLootTable(String path, String jsonContent) { files.put("loot/" + path, jsonContent); }
        @Override public void addAdvancement(String path, String jsonContent) { files.put("adv/" + path, jsonContent); }
        @Override public void addFunction(String path, String commands) { files.put("fn/" + path, commands); }
        @Override public void addData(String relPath, String jsonContent) { files.put(relPath, jsonContent); }
        @Override public void addTag(String tagType, String tagId, java.util.List<String> values) {}
        @Override public Map<String, String> getVirtualFiles() { return files; }
        @Override public void clear() { files.clear(); }
    }

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
    }

    @Test
    void applyAll_WritesDimensionTypeAndDimensionJsons() {
        ILuaEngine engine = new CobaltLuaEngine();
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('arcane:crystal_realm', {\n" +
            "    hasSkyLight = false,\n" +
            "    skyColor = 0xFF88FF,\n" +
            "    seaLevel = 40,\n" +
            "    minHeight = -32,\n" +
            "    maxHeight = 128,\n" +
            "    surfaceBlock = 'arcane:crystal_grass'\n" +
            "})\n",
            "setup"
        );

        Object serviceObj = LuaServiceRegistry.get("DimensionServiceImpl");
        assertTrue(serviceObj instanceof DimensionServiceImpl, "DimensionServiceImpl must be in the registry");

        FakeDatapack datapack = new FakeDatapack();
        NeoForgeDimensionProvider.applyAll(datapack);

        String typeJson = datapack.files.get("data/arcane/dimension_type/crystal_realm.json");
        assertNotNull(typeJson, "dimension type JSON must be written");
        assertTrue(typeJson.contains("\"has_skylight\":false"), "type JSON must carry has_skylight");
        assertTrue(typeJson.contains("\"min_y\":-32"), "type JSON must carry min_y");
        assertTrue(typeJson.contains("\"height\":160"), "type JSON must carry height = maxY - minY");
        assertTrue(typeJson.contains("\"coordinate_scale\":1.0"), "type JSON must carry coordinate_scale");
        assertTrue(typeJson.contains("\"effects\":\"luatweaker:lua\""), "type JSON must reference the custom sky effects");

        String dimensionJson = datapack.files.get("data/arcane/dimension/crystal_realm.json");
        assertNotNull(dimensionJson, "dimension JSON must be written");
        // CRITICAL: the dimension must reference ITS OWN dimension type, so the
        // custom min_y/height/seaLevel settings actually apply.
        assertTrue(dimensionJson.contains("\"type\":\"arcane:crystal_realm\""),
                "dimension must reference its own custom dimension type");
        assertTrue(dimensionJson.contains("\"type\":\"luatweaker:lua\""), "generator must use the Lua codec");
        assertTrue(dimensionJson.contains("\"dimension_id\":\"arcane:crystal_realm\""), "generator must carry the dimension id");
        assertTrue(dimensionJson.contains("\"biome_source\""), "generator must embed a biome source");
    }

    @Test
    void applyAll_WritesCustomDimensionTypeFields() {
        ILuaEngine engine = new CobaltLuaEngine();
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('minecraft:custom', {\n" +
            "    piglinSafe = true,\n" +
            "    hasRaids = true,\n" +
            "    monsterSpawnLightLevel = 5,\n" +
            "    monsterSpawnBlockLightLimit = 3,\n" +
            "    infiniburn = '#minecraft:infiniburn_nether',\n" +
            "    effectsLocation = 'minecraft:the_nether'\n" +
            "})\n",
            "setup_custom_type"
        );

        FakeDatapack datapack = new FakeDatapack();
        NeoForgeDimensionProvider.applyAll(datapack);

        String typeJson = datapack.files.get("data/minecraft/dimension_type/custom.json");
        assertNotNull(typeJson, "dimension type JSON must be written");
        assertTrue(typeJson.contains("\"piglin_safe\":true"), "piglin_safe must come from config");
        assertTrue(typeJson.contains("\"has_raids\":true"), "has_raids must come from config");
        assertTrue(typeJson.contains("\"monster_spawn_light_level\":{\"type\":\"minecraft:constant\",\"value\":5}"),
                "monster light level must be a keyed IntProvider (1.21.1 vanilla format)");
        assertTrue(typeJson.contains("\"monster_spawn_block_light_limit\":3"), "block light limit must come from config");
        assertTrue(typeJson.contains("\"infiniburn\":\"#minecraft:infiniburn_nether\""), "infiniburn must come from config");
        assertTrue(typeJson.contains("\"effects\":\"minecraft:the_nether\""), "effects must come from config");
    }

    @Test
    void applyAll_WithNoDimensionsWritesNothing() {
        ILuaEngine engine = new CobaltLuaEngine();
        DimensionLuaBinding.registerBindings(engine);
        FakeDatapack datapack = new FakeDatapack();
        NeoForgeDimensionProvider.applyAll(datapack);
        assertTrue(datapack.files.isEmpty(), "no dimensions -> no datapack files");
    }
}

