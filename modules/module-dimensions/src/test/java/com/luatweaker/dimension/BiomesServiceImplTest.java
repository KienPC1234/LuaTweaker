package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesServiceImplTest {

    private static final String CRYSTAL_PLAINS_PATH = "data/luatweaker/worldgen/biome/crystal_plains.json";
    private static final String CRYSTAL_PLAINS_ID = "luatweaker:crystal_plains";
    private static final String MONSTER_CATEGORY = "monster";
    private static final String GOLEM_ID = "luatweaker:crystal_golem";
    private static final String ZOMBIE_ID = "minecraft:zombie";
    private static final String SEED_JSON =
        "{\"has_precipitation\":true,\"temperature\":0.7,\"downfall\":0.4,\"effects\":{},"
            + "\"spawners\":{},\"spawn_costs\":{},\"carvers\":{},\"features\":[]}";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private BiomesServiceImpl service;
    private BiomesFakeDatapack datapack;

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
        ILuaEngine engine = LuaEngineFactory.create(false);
        service = new BiomesServiceImpl(engine);
        datapack = new BiomesFakeDatapack();
        engine.registerService("Datapack", datapack);
    }

    @Test
    void addSpawnWritesEntityIntoExistingBiomeJson() {
        datapack.files.put(CRYSTAL_PLAINS_PATH, SEED_JSON);

        service.addSpawn(CRYSTAL_PLAINS_ID, MONSTER_CATEGORY, GOLEM_ID, 3, 1, 1);

        String updated = datapack.files.get(CRYSTAL_PLAINS_PATH);
        assertTrue(updated.contains("\"" + GOLEM_ID + "\""), "entity must be added to the biome JSON");
    }

    @Test
    void addSpawnWritesWeightValue() {
        datapack.files.put(CRYSTAL_PLAINS_PATH, SEED_JSON);

        service.addSpawn(CRYSTAL_PLAINS_ID, MONSTER_CATEGORY, GOLEM_ID, 3, 1, 1);

        String updated = datapack.files.get(CRYSTAL_PLAINS_PATH);
        assertTrue(updated.contains("\"weight\":3"), "weight must be written to the spawn entry");
    }

    @Test
    void addSpawnPreservesExistingBiomeFields() {
        datapack.files.put(CRYSTAL_PLAINS_PATH, SEED_JSON);

        service.addSpawn(CRYSTAL_PLAINS_ID, MONSTER_CATEGORY, GOLEM_ID, 3, 1, 1);

        String updated = datapack.files.get(CRYSTAL_PLAINS_PATH);
        assertTrue(updated.contains("\"has_precipitation\":true"), "existing biome fields must be preserved");
    }

    @Test
    void addSpawnResultIsNotNull() {
        datapack.files.put(CRYSTAL_PLAINS_PATH, SEED_JSON);

        service.addSpawn(CRYSTAL_PLAINS_ID, MONSTER_CATEGORY, GOLEM_ID, 3, 1, 1);

        assertNotNull(datapack.files.get(CRYSTAL_PLAINS_PATH), "merged biome JSON must be stored");
    }

    @Test
    void removeSpawnRemovesEntry() {
        datapack.files.put(CRYSTAL_PLAINS_PATH,
            "{\"spawners\":{\"" + MONSTER_CATEGORY + "\":[{\"type\":\"" + GOLEM_ID
                + "\",\"weight\":3,\"minCount\":1,\"maxCount\":1},{\"type\":\"" + ZOMBIE_ID
                + "\",\"weight\":8,\"minCount\":1,\"maxCount\":3}]}}");

        service.removeSpawn(CRYSTAL_PLAINS_ID, MONSTER_CATEGORY, GOLEM_ID);

        String updated = datapack.files.get(CRYSTAL_PLAINS_PATH);
        assertFalse(updated.contains("crystal_golem"), "entity must be removed from the biome JSON");
    }

    @Test
    void removeSpawnKeepsOtherEntries() {
        datapack.files.put(CRYSTAL_PLAINS_PATH,
            "{\"spawners\":{\"" + MONSTER_CATEGORY + "\":[{\"type\":\"" + GOLEM_ID
                + "\",\"weight\":3,\"minCount\":1,\"maxCount\":1},{\"type\":\"" + ZOMBIE_ID
                + "\",\"weight\":8,\"minCount\":1,\"maxCount\":3}]}}");

        service.removeSpawn(CRYSTAL_PLAINS_ID, MONSTER_CATEGORY, GOLEM_ID);

        String updated = datapack.files.get(CRYSTAL_PLAINS_PATH);
        assertTrue(updated.contains(ZOMBIE_ID), "other spawn entries must survive removal");
    }
}
