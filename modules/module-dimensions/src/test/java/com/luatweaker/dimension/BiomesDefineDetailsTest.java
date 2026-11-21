package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesDefineDetailsTest {

    private static final String MEADOW_PATH = "data/mymod/worldgen/biome/crystal_meadow.json";
    private static final String MEADOW_ID = "mymod:crystal_meadow";

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

    private String defineMeadow() {
        service.define(MEADOW_ID, BiomesDefineFixture.defineConfig(engine));
        return datapack.files.get(MEADOW_PATH);
    }

    @Test
    void defineWritesParticleType() {
        assertTrue(defineMeadow().contains("\"type\":\"minecraft:cherry_leaves\""),
            "mood particle type must be written");
    }

    @Test
    void defineWritesSpawnerEntry() {
        assertTrue(defineMeadow().contains("\"type\":\"mymod:wisp\""), "spawner entry must be written");
    }

    @Test
    void defineWritesSpawnCostCharge() {
        assertTrue(defineMeadow().contains("\"charge\":1.0"), "spawn cost charge must be written");
    }

    @Test
    void defineWritesCaveCarver() {
        assertTrue(defineMeadow().contains("\"minecraft:cave\""), "air carver must be written");
    }

    @Test
    void defineWritesCrystalTreesFeature() {
        assertTrue(defineMeadow().contains("\"mymod:crystal_trees\""), "tree feature must be written");
    }

    @Test
    void defineWritesOneSlotPerGenerationStep() {
        JsonObject parsed = JsonParser.parseString(defineMeadow()).getAsJsonObject();

        assertEquals(11, parsed.getAsJsonArray("features").size(),
            "features must have one slot per generation step");
    }

    @Test
    void defineWritesVegetalDecorationAtStepNine() {
        JsonObject parsed = JsonParser.parseString(defineMeadow()).getAsJsonObject();

        assertTrue(parsed.getAsJsonArray("features").get(9).toString().contains("mymod:crystal_trees"),
            "vegetal decoration is step ordinal 9");
    }
}
