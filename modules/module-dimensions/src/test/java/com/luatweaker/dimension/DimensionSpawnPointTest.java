package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies spawn-point parsing, overriding and validation. */
class DimensionSpawnPointTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void spawnPointOverrideReplacesConfigValue() {
        ILuaTable config = engine.createTable();
        config.rawset("spawnX", 100);
        config.rawset("spawnZ", 200);
        service.create(DIMENSION_ID, config);
        DimensionServiceImpl.SpawnPoint configured = service.getSpawnPoint(DIMENSION_ID);

        service.setSpawnPoint(DIMENSION_ID, 500, -500);
        DimensionServiceImpl.SpawnPoint overridden = service.getSpawnPoint(DIMENSION_ID);

        assertEquals(List.of(100, 500, -500),
                List.of(configured.x(), overridden.x(), overridden.z()),
                "setSpawnPoint must replace the spawn point parsed from config");
    }

    @Test
    void spawnPointIsNullWhenUnset() {
        service.create(DIMENSION_ID, engine.createTable());

        assertNull(service.getSpawnPoint(DIMENSION_ID),
                "a dimension without a spawn point must report null");
    }

    @Test
    void spawnPointRejectsInvalidDimensionId() {
        assertThrows(IllegalArgumentException.class, () -> service.setSpawnPoint("", 0, 0),
                "setting a spawn point on an invalid dimension id must be rejected");
    }
}
