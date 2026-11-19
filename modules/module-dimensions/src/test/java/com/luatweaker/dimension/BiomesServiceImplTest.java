package com.luatweaker.dimension;

import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

public class BiomesServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private static class FakeDatapack implements IDatapackService {
        final Map<String, String> files = new ConcurrentHashMap<>();

        @Override public void addJsonRecipe(String recipeId, String jsonContent) {}
        @Override public void addLootTable(String path, String jsonContent) {}
        @Override public void addAdvancement(String path, String jsonContent) {}
        @Override public void addFunction(String path, String commands) {}
        @Override public void addData(String relPath, String jsonContent) { files.put(relPath, jsonContent); }
        @Override public void addTag(String tagType, String tagId, java.util.List<String> values) {}
        @Override public Map<String, String> getVirtualFiles() { return files; }
        @Override public void clear() { files.clear(); }
    }

    private ILuaEngine engine;
    private BiomesServiceImpl service;
    private FakeDatapack datapack;

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
        engine = LuaEngineFactory.create(false);
        service = new BiomesServiceImpl(engine);
        datapack = new FakeDatapack();
        engine.registerService("Datapack", datapack);
    }

    @Test
    void addSpawn_MergesIntoExistingBiomeJson() {
        datapack.files.put("data/luatweaker/worldgen/biome/crystal_plains.json",
                "{\"has_precipitation\":true,\"temperature\":0.7,\"downfall\":0.4,\"effects\":{},\"spawners\":{},\"spawn_costs\":{},\"carvers\":{},\"features\":[]}");

        service.addSpawn("luatweaker:crystal_plains", "monster", "luatweaker:crystal_golem", 3, 1, 1);

        String updated = datapack.files.get("data/luatweaker/worldgen/biome/crystal_plains.json");
        assertNotNull(updated);
        assertTrue(updated.contains("\"luatweaker:crystal_golem\""), "entity must be added to the biome JSON");
        assertTrue(updated.contains("\"weight\":3"), "weight must be written");
        assertTrue(updated.contains("\"has_precipitation\":true"), "existing biome fields must be preserved");
    }

    @Test
    void addSpawn_RejectsUnknownBiomeInsteadOfShadowing() {
        // Writing a minimal file for an unknown id could silently shadow a
        // vanilla (or other-mod) biome with an empty definition.
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn("minecraft:plains", "monster", "minecraft:zombie", 10, 1, 1));
        assertTrue(error.getMessage().contains("Define"), error.getMessage());

        error = assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn("luatweaker:never_defined", "monster", "minecraft:zombie", 10, 1, 1));
        assertTrue(error.getMessage().contains("Define"), error.getMessage());

        assertThrows(IllegalArgumentException.class, () ->
            service.removeSpawn("luatweaker:never_defined", "monster", "minecraft:zombie"));
    }

    @Test
    void addSpawn_RejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("bad id", "monster", "minecraft:zombie", 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("minecraft:plains", "not-a-category", "minecraft:zombie", 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("minecraft:plains", "monster", "minecraft:zombie", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("minecraft:plains", "monster", "minecraft:zombie", 1, 3, 1));
    }

    // ---------- define ----------

    private ILuaTable defineConfig() {
        ILuaTable effects = engine.createTable();
        effects.rawset("sky_color", 5040991);
        effects.rawset("fog_color", 12638463);
        effects.rawset("water_color", 4159204);
        effects.rawset("water_fog_color", 329011);
        effects.rawset("grass_color_modifier", "swamp");

        ILuaTable music = engine.createTable();
        music.rawset("sound", "mymod:music.crystal");
        music.rawset("min_delay", 1200);
        music.rawset("max_delay", 3600);
        effects.rawset("music", music);

        ILuaTable particle = engine.createTable();
        particle.rawset("type", "minecraft:cherry_leaves");
        particle.rawset("probability", 0.02);
        effects.rawset("particle", particle);

        ILuaTableEntry monsterEntry = new ILuaTableEntry("mymod:wisp", 10, 1, 2);

        ILuaTable spawners = engine.createTable();
        spawners.rawset("monster", monsterEntry.list(engine));

        ILuaTable costs = engine.createTable();
        ILuaTable wispCost = engine.createTable();
        wispCost.rawset("charge", 1.0);
        wispCost.rawset("energy_budget", 0.5);
        costs.rawset("mymod:wisp", wispCost);

        ILuaTable carvers = engine.createTable();
        carvers.rawset("air", stringList("minecraft:cave", "minecraft:canyon"));

        ILuaTable features = engine.createTable();
        features.rawset("vegetal_decoration", stringList("mymod:crystal_trees", "minecraft:flower_plain"));
        features.rawset("underground_ores", stringList("minecraft:ore_coal_upper"));

        ILuaTable config = engine.createTable();
        config.rawset("temperature", 0.9);
        config.rawset("downfall", 0.7);
        config.rawset("effects", effects);
        config.rawset("spawners", spawners);
        config.rawset("spawn_costs", costs);
        config.rawset("carvers", carvers);
        config.rawset("features", features);
        return config;
    }

    private ILuaTable stringList(String... ids) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < ids.length; i++) {
            table.rawset(i + 1, ids[i]);
        }
        return table;
    }

    private static final class ILuaTableEntry {
        final String type;
        final int weight;
        final int min;
        final int max;

        ILuaTableEntry(String type, int weight, int min, int max) {
            this.type = type;
            this.weight = weight;
            this.min = min;
            this.max = max;
        }

        ILuaTable list(ILuaEngine engine) {
            ILuaTable entry = engine.createTable();
            entry.rawset("type", type);
            entry.rawset("weight", weight);
            entry.rawset("minCount", min);
            entry.rawset("maxCount", max);
            ILuaTable list = engine.createTable();
            list.rawset(1, entry);
            return list;
        }
    }

    @Test
    void define_WritesFullBiomeJson() {
        service.define("mymod:crystal_meadow", defineConfig());

        String json = datapack.files.get("data/mymod/worldgen/biome/crystal_meadow.json");
        assertNotNull(json, "Define must write the biome JSON");
        assertTrue(json.contains("\"temperature\":0.9"), json);
        assertTrue(json.contains("\"downfall\":0.7"), json);
        assertTrue(json.contains("\"has_precipitation\":true"), json);
        assertTrue(json.contains("\"sky_color\":5040991"), json);
        assertTrue(json.contains("\"grass_color_modifier\":\"swamp\""), json);
        assertTrue(json.contains("\"sound\":\"mymod:music.crystal\""), json);
        assertTrue(json.contains("\"type\":\"minecraft:cherry_leaves\""), json);
        assertTrue(json.contains("\"type\":\"mymod:wisp\""), json);
        assertTrue(json.contains("\"charge\":1.0"), json);
        assertTrue(json.contains("\"minecraft:cave\""), json);
        assertTrue(json.contains("\"mymod:crystal_trees\""), json);
        com.google.gson.JsonObject parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertEquals(11, parsed.getAsJsonArray("features").size(), "features must have one slot per generation step");
        assertTrue(parsed.getAsJsonArray("features").get(9).toString().contains("mymod:crystal_trees"),
            "vegetal_decoration is step ordinal 9");
    }

    @Test
    void define_ThenAddSpawnMerges() {
        service.define("mymod:meadow", engine.createTable());
        service.addSpawn("mymod:meadow", "creature", "minecraft:parrot", 10, 1, 2);

        String json = datapack.files.get("data/mymod/worldgen/biome/meadow.json");
        assertTrue(json.contains("\"minecraft:parrot\""), json);
        assertTrue(json.contains("\"temperature\""), "Define fields must survive the merge");
    }

    @Test
    void define_RejectsBadStepsAndIds() {
        assertThrows(IllegalArgumentException.class, () -> service.define("bad id", engine.createTable()));
        assertThrows(IllegalArgumentException.class, () -> service.define(null, engine.createTable()));

        ILuaTable badStep = engine.createTable();
        ILuaTable features = engine.createTable();
        features.rawset("not_a_step", stringList("minecraft:ore_dirt"));
        badStep.rawset("features", features);
        assertThrows(IllegalArgumentException.class, () -> service.define("mymod:bad", badStep));

        ILuaTable badTemp = engine.createTable();
        badTemp.rawset("temperature", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.define("mymod:bad", badTemp));
    }

    @Test
    void removeSpawn_RemovesEntry() {
        datapack.files.put("data/luatweaker/worldgen/biome/crystal_plains.json",
                "{\"spawners\":{\"monster\":[{\"type\":\"luatweaker:crystal_golem\",\"weight\":3,\"minCount\":1,\"maxCount\":1},{\"type\":\"minecraft:zombie\",\"weight\":8,\"minCount\":1,\"maxCount\":3}]}}");

        service.removeSpawn("luatweaker:crystal_plains", "monster", "luatweaker:crystal_golem");

        String updated = datapack.files.get("data/luatweaker/worldgen/biome/crystal_plains.json");
        assertFalse(updated.contains("crystal_golem"), "entity must be removed");
        assertTrue(updated.contains("minecraft:zombie"), "other entries must survive");
    }

    @Test
    void getSpawns_ReturnsEntries() {
        datapack.files.put("data/luatweaker/worldgen/biome/crystal_plains.json",
                "{\"spawners\":{\"monster\":[{\"type\":\"minecraft:zombie\",\"weight\":8,\"minCount\":1,\"maxCount\":3}]}}");

        ILuaValue spawns = (ILuaValue) service.getSpawns("luatweaker:crystal_plains");
        assertTrue(spawns.isTable());
        ILuaValue monster = spawns.asTable().rawget("monster");
        assertNotNull(monster);
        assertEquals("minecraft:zombie", monster.asTable().rawget(1).asTable().rawget("type").asString());
    }
}

