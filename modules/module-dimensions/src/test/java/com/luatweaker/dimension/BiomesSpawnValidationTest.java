package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesSpawnValidationTest {

    private static final String MONSTER_CATEGORY = "monster";
    private static final String PLAINS_ID = "minecraft:plains";
    private static final String ZOMBIE_ID = "minecraft:zombie";
    private static final String NEVER_DEFINED_ID = "luatweaker:never_defined";

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
    void addSpawnRejectsUnknownVanillaBiome() {
        // Writing a minimal file for an unknown id could silently shadow a
        // vanilla (or other-mod) biome with an empty definition.
        assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn(PLAINS_ID, MONSTER_CATEGORY, ZOMBIE_ID, 10, 1, 1),
            "adding a spawn to an undefined vanilla biome must be rejected");
    }

    @Test
    void unknownBiomeErrorMentionsDefine() {
        String message = "";
        try {
            service.addSpawn(PLAINS_ID, MONSTER_CATEGORY, ZOMBIE_ID, 10, 1, 1);
        } catch (IllegalArgumentException expected) {
            message = String.valueOf(expected.getMessage());
        }

        assertTrue(message.contains("Define"), "rejection error must point the user at Define");
    }

    @Test
    void addSpawnRejectsUndefinedModBiome() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn(NEVER_DEFINED_ID, MONSTER_CATEGORY, ZOMBIE_ID, 10, 1, 1),
            "adding a spawn to a biome that was never defined must be rejected");
    }

    @Test
    void removeSpawnRejectsUnknownBiome() {
        assertThrows(IllegalArgumentException.class, () ->
            service.removeSpawn(NEVER_DEFINED_ID, MONSTER_CATEGORY, ZOMBIE_ID),
            "removing a spawn from an undefined biome must be rejected");
    }
}
