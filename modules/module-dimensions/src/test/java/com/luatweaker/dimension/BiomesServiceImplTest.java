package com.luatweaker.dimension;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.api.vm.ILuaEngine;
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
        engine = new CobaltLuaEngine();
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
    void addSpawn_CreatesMinimalBiomeWhenMissing() {
        service.addSpawn("luatweaker:new_biome", "creature", "minecraft:parrot", 10, 1, 2);
        String updated = datapack.files.get("data/luatweaker/worldgen/biome/new_biome.json");
        assertNotNull(updated, "missing biome JSON must be created");
        assertTrue(updated.contains("\"minecraft:parrot\""));
        assertTrue(updated.contains("\"creature\""));
        assertTrue(updated.contains("\"temperature\""));
    }

    @Test
    void addSpawn_RejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("bad id", "monster", "minecraft:zombie", 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("minecraft:plains", "not-a-category", "minecraft:zombie", 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("minecraft:plains", "monster", "minecraft:zombie", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> service.addSpawn("minecraft:plains", "monster", "minecraft:zombie", 1, 3, 1));
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

