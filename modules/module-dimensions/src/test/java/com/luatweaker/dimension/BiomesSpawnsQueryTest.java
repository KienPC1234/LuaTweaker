package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesSpawnsQueryTest {

    private static final String CRYSTAL_PLAINS_PATH = "data/luatweaker/worldgen/biome/crystal_plains.json";
    private static final String CRYSTAL_PLAINS_ID = "luatweaker:crystal_plains";
    private static final String MONSTER_CATEGORY = "monster";
    private static final String ZOMBIE_ID = "minecraft:zombie";
    private static final String SPAWNERS_JSON =
        "{\"spawners\":{\"" + MONSTER_CATEGORY + "\":[{\"type\":\"" + ZOMBIE_ID
            + "\",\"weight\":8,\"minCount\":1,\"maxCount\":3}]}}";

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
    void spawnListingIsATable() {
        datapack.files.put(CRYSTAL_PLAINS_PATH, SPAWNERS_JSON);

        Object spawns = service.getSpawns(CRYSTAL_PLAINS_ID);

        assertTrue(spawns instanceof ILuaValue && ((ILuaValue) spawns).isTable(),
            "spawn listing must be returned as a Lua table");
    }

    @Test
    void spawnListingContainsMonsterCategory() {
        datapack.files.put(CRYSTAL_PLAINS_PATH, SPAWNERS_JSON);

        ILuaValue spawns = (ILuaValue) service.getSpawns(CRYSTAL_PLAINS_ID);

        assertNotNull(spawns.asTable().rawget(MONSTER_CATEGORY), "monster category must be present");
    }

    @Test
    void spawnListingEntryTypeMatches() {
        datapack.files.put(CRYSTAL_PLAINS_PATH, SPAWNERS_JSON);

        ILuaValue spawns = (ILuaValue) service.getSpawns(CRYSTAL_PLAINS_ID);

        assertEquals(ZOMBIE_ID,
            spawns.asTable().rawget(MONSTER_CATEGORY).asTable().rawget(1).asTable().rawget("type").asString(),
            "spawn entry type must match the stored zombie id");
    }
}
