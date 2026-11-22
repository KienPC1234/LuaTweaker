package com.luatweaker.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BiomesDefineSectionsValidationTest {

    private static final String MEADOW_ID = "mymod:meadow";
    private static final String MEADOW_PATH = "data/mymod/worldgen/biome/meadow.json";
    private static final String MALFORMED_JSON = "{oops";

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

    @Test
    void defineRejectsNegativeSpawnCharge() {
        ILuaTable cost = engine.createTable();
        cost.rawset("charge", -1.0);
        cost.rawset("energy_budget", 0.5);
        ILuaTable costs = engine.createTable();
        costs.rawset("mymod:wisp", cost);
        ILuaTable config = engine.createTable();
        config.rawset("spawn_costs", costs);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "negative spawn charge must be rejected");
    }

    @Test
    void defineRejectsUnknownCarverKind() {
        ILuaTable carvers = engine.createTable();
        carvers.rawset("stone", BiomesDefineFixture.stringList(engine, "minecraft:cave"));
        ILuaTable config = engine.createTable();
        config.rawset("carvers", carvers);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "carver kind outside air/liquid must be rejected");
    }

    @Test
    void defineRejectsUnknownFeatureStep() {
        ILuaTable features = engine.createTable();
        features.rawset("nether", BiomesDefineFixture.stringList(engine, "minecraft:ore_coal_upper"));
        ILuaTable config = engine.createTable();
        config.rawset("features", features);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "unknown generation step must be rejected");
    }

    @Test
    void defineRejectsInvalidFeatureTag() {
        ILuaTable features = engine.createTable();
        features.rawset("vegetal_decoration", BiomesDefineFixture.stringList(engine, "#bad tag"));
        ILuaTable config = engine.createTable();
        config.rawset("features", features);
        assertThrows(IllegalArgumentException.class, () ->
            service.define(MEADOW_ID, config),
            "feature tag with a space must be rejected");
    }

    @Test
    void addSpawnRejectsMalformedExistingJson() {
        datapack.addData(MEADOW_PATH, MALFORMED_JSON);
        assertThrows(IllegalArgumentException.class, () ->
            service.addSpawn(MEADOW_ID, "monster", "minecraft:zombie", 1, 1, 1),
            "malformed stored biome JSON must be rejected loudly");
    }

    @Test
    void malformedJsonErrorMentionsMalformed() {
        datapack.addData(MEADOW_PATH, MALFORMED_JSON);
        String message = "";
        try {
            service.addSpawn(MEADOW_ID, "monster", "minecraft:zombie", 1, 1, 1);
        } catch (IllegalArgumentException expected) {
            message = String.valueOf(expected.getMessage());
        }
        assertTrue(message.contains("malformed JSON"), "malformed JSON error must say so");
    }

    @Test
    void malformedJsonStillReturnsTable() {
        datapack.addData(MEADOW_PATH, MALFORMED_JSON);
        Object result = service.getSpawns(MEADOW_ID);
        assertTrue(result instanceof ILuaTable, "spawn query must still return a table");
    }

    @Test
    void malformedJsonYieldsEmptySpawnView() {
        datapack.addData(MEADOW_PATH, MALFORMED_JSON);
        Object result = service.getSpawns(MEADOW_ID);
        assertEquals(0, ((ILuaTable) result).length(), "malformed JSON must yield an empty spawn view");
    }
}
