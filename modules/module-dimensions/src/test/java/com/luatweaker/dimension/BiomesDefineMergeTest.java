package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesDefineMergeTest {

    private static final String MEADOW_PATH = "data/mymod/worldgen/biome/meadow.json";
    private static final String MEADOW_ID = "mymod:meadow";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine engine;
    private BiomesServiceImpl service;
    private BiomesFakeDatapack datapack;

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
        engine = LuaEngineFactory.create(false);
        service = new BiomesServiceImpl(engine);
        datapack = new BiomesFakeDatapack();
        engine.registerService("Datapack", datapack);
    }

    private String defineThenAddParrot() {
        service.define(MEADOW_ID, engine.createTable());
        service.addSpawn(MEADOW_ID, "creature", "minecraft:parrot", 10, 1, 2);
        return datapack.getData(MEADOW_PATH);
    }

    @Test
    void defineThenAddSpawnMergesParrot() {
        assertTrue(defineThenAddParrot().contains("\"minecraft:parrot\""), "added spawn must merge");
    }

    @Test
    void defineThenAddSpawnKeepsDefinedFields() {
        assertTrue(defineThenAddParrot().contains("\"temperature\""), "Define fields must survive the merge");
    }
}
