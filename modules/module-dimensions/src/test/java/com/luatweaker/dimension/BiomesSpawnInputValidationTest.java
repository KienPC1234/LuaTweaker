package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesSpawnInputValidationTest {

    private static final String MONSTER_CATEGORY = "monster";
    private static final String PLAINS_ID = "minecraft:plains";
    private static final String ZOMBIE_ID = "minecraft:zombie";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private BiomesServiceImpl service;

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
        ILuaEngine engine = LuaEngineFactory.create(false);
        service = new BiomesServiceImpl(engine);
        engine.registerService("Datapack", new BiomesFakeDatapack());
    }

    @Test
    void addSpawnRejectsBadId() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn("bad id", MONSTER_CATEGORY, ZOMBIE_ID, 1, 1, 1),
            "biome id with a space must be rejected");
    }

    @Test
    void addSpawnRejectsBadCategory() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn(PLAINS_ID, "not-a-category", ZOMBIE_ID, 1, 1, 1),
            "unknown spawn category must be rejected");
    }

    @Test
    void addSpawnRejectsZeroWeight() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn(PLAINS_ID, MONSTER_CATEGORY, ZOMBIE_ID, 0, 1, 1),
            "zero weight must be rejected");
    }

    @Test
    void addSpawnRejectsMinAboveMax() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn(PLAINS_ID, MONSTER_CATEGORY, ZOMBIE_ID, 1, 3, 1),
            "min count above max count must be rejected");
    }
}
