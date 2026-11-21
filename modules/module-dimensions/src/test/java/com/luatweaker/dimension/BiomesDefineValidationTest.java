package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesDefineValidationTest {

    private static final String MEADOW_ID = "mymod:meadow";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine engine;
    private BiomesServiceImpl service;

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
        engine = LuaEngineFactory.create(false);
        service = new BiomesServiceImpl(engine);
        engine.registerService("Datapack", new BiomesFakeDatapack());
    }

    @Test
    void defineRejectsBadId() {
        assertThrows(IllegalArgumentException.class, () ->
            service.define("bad id", engine.createTable()),
            "biome id with a space must be rejected");
    }

    @Test
    void defineRejectsNaNTemperature() {
        ILuaTable config = engine.createTable();
        config.rawset("temperature", Double.NaN);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "NaN temperature must be rejected");
    }

    @Test
    void defineRejectsDownfallOutOfRange() {
        ILuaTable config = engine.createTable();
        config.rawset("downfall", 2.0);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "downfall above 1.0 must be rejected");
    }

    @Test
    void defineRejectsBadGrassModifier() {
        ILuaTable effects = engine.createTable();
        effects.rawset("grass_color_modifier", "lava");
        ILuaTable config = engine.createTable();
        config.rawset("effects", effects);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "unknown grass color modifier must be rejected");
    }

    @Test
    void defineRejectsBadParticleType() {
        ILuaTable particle = engine.createTable();
        particle.rawset("type", "bad id");
        particle.rawset("probability", 0.02);
        ILuaTable config = effectsConfig(particle);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "particle type with a space must be rejected");
    }

    @Test
    void defineRejectsBadParticleProbability() {
        ILuaTable particle = engine.createTable();
        particle.rawset("type", "minecraft:cherry_leaves");
        particle.rawset("probability", 2.0);
        ILuaTable config = effectsConfig(particle);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "particle probability above 1.0 must be rejected");
    }

    @Test
    void defineRejectsUnknownSpawnerCategory() {
        ILuaTable spawners = engine.createTable();
        spawners.rawset("boss", BiomesDefineFixture.stringList(engine, "mymod:wisp"));
        ILuaTable config = engine.createTable();
        config.rawset("spawners", spawners);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "unknown spawner category must be rejected");
    }

    private ILuaTable effectsConfig(ILuaTable particle) {
        ILuaTable effects = engine.createTable();
        effects.rawset("particle", particle);
        ILuaTable config = engine.createTable();
        config.rawset("effects", effects);
        return config;
    }
}
