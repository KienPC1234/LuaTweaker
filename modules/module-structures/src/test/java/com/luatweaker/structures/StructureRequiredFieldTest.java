package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureRequiredFieldTest {

    private static final String STRUCTURE_ID = "mymod:x";
    private static final String STRUCTURE_TEMPLATE = "mymod:structures/x";
    private static final String PLAINS = "minecraft:plains";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private StructureTestFixture fixture;

    @BeforeEach
    void setup() {
        fixture = new StructureTestFixture();
    }

    private ILuaTable baseConfig() {
        return fixture.minimalConfig(STRUCTURE_TEMPLATE);
    }

    @Test
    void registerRequiresTemplate() {
        ILuaTable config = fixture.engine.createTable();
        fixture.withTable(config, "spawnBiomes", fixture.stringList(PLAINS));

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a config without a template must be rejected");
    }

    @Test
    void registerRequiresSpawnBiomes() {
        ILuaTable config = fixture.engine.createTable();
        fixture.withString(config, "template", STRUCTURE_TEMPLATE);

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a config without spawnBiomes must be rejected");
    }

    @Test
    void registerRejectsEmptySpawnBiomes() {
        ILuaTable config = fixture.withTable(baseConfig(), "spawnBiomes", fixture.engine.createTable());

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "an empty spawnBiomes list must be rejected");
    }

    @Test
    void registerRejectsInvalidBiomeId() {
        ILuaTable config = fixture.withTable(baseConfig(), "spawnBiomes", fixture.stringList("not a biome!"));

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "an invalid biome resource location must be rejected");
    }

    @Test
    void registerRejectsNonTableLootTables() {
        ILuaTable config = fixture.withString(baseConfig(), "lootTables", "not-a-table");

        assertThrows(IllegalArgumentException.class,
            () -> fixture.service.register(STRUCTURE_ID, config),
            "a non-table lootTables value must be rejected");
    }
}
