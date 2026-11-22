package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesDefineCoreTest {

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
        return datapack.getData(MEADOW_PATH);
    }

    @Test
    void defineWritesBiomeJson() {
        assertNotNull(defineMeadow(), "Define must write the biome JSON");
    }

    @Test
    void defineWritesTemperature() {
        assertTrue(defineMeadow().contains("\"temperature\":0.9"), "temperature must be written");
    }

    @Test
    void defineWritesDownfall() {
        assertTrue(defineMeadow().contains("\"downfall\":0.7"), "downfall must be written");
    }

    @Test
    void defineWritesPrecipitationFlag() {
        assertTrue(defineMeadow().contains("\"has_precipitation\":true"), "precipitation flag must default on");
    }

    @Test
    void defineWritesSkyColor() {
        assertTrue(defineMeadow().contains("\"sky_color\":5040991"), "sky color must be written");
    }

    @Test
    void defineWritesGrassColorModifier() {
        assertTrue(defineMeadow().contains("\"grass_color_modifier\":\"swamp\""),
            "grass color modifier must be written");
    }

    @Test
    void defineWritesMusicSound() {
        assertTrue(defineMeadow().contains("\"sound\":\"mymod:music.crystal\""), "music sound must be written");
    }
}
