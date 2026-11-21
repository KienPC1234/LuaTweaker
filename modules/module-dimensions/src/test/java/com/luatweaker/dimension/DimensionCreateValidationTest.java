package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that dimension creation rejects malformed ids, configs and field values
 * with {@link IllegalArgumentException} before anything reaches the platform.
 */
class DimensionCreateValidationTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void createRejectsMalformedDimensionIds() {
        for (String id : List.of("", "UPPER:case", "bad id!")) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(id, engine.createTable()),
                    "dimension id must be rejected: " + id);
        }
    }

    @Test
    void createRejectsNonTableConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(DIMENSION_ID, engine.wrapNumber(42)),
                "a non-table dimension config must be rejected");
    }

    @Test
    void createRejectsInvalidColor() {
        ILuaTable config = engine.createTable();
        config.rawset("skyColor", 0xFFFFFFF);

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "a sky color outside 0xFFFFFF must be rejected");
    }

    @Test
    void createRejectsInvalidHeightRange() {
        ILuaTable config = engine.createTable();
        config.rawset("minHeight", 100);
        config.rawset("maxHeight", 50);

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "minHeight above maxHeight must be rejected");
    }

    @Test
    void createRejectsUnsupportedTerrainType() {
        ILuaTable config = engine.createTable();
        config.rawset("terrain", "vanilla");

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "an unknown terrain type must be rejected");
    }

    @Test
    void createRejectsBiomeEntryWithoutId() {
        ILuaTable biome = engine.createTable();
        biome.rawset("weight", 5);
        ILuaTable biomes = engine.createTable();
        biomes.rawset(1, biome);
        ILuaTable config = engine.createTable();
        config.rawset("biomes", biomes);

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "a biome entry without an id must be rejected");
    }

    @Test
    void createRejectsNegativeAmbientLight() {
        ILuaTable config = engine.createTable();
        config.rawset("ambientLight", -0.5);

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "negative ambient light must be rejected");
    }

    @Test
    void createRejectsInvalidSurfaceBlock() {
        ILuaTable config = engine.createTable();
        config.rawset("surfaceBlock", "not a valid id");

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "an invalid surface block id must be rejected");
    }

    @Test
    void createRejectsInvalidEffectsLocation() {
        ILuaTable config = engine.createTable();
        config.rawset("effectsLocation", "no-namespace");

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "a non-namespaced effects location must be rejected");
    }

    @Test
    void createRejectsPartialSpawnPair() {
        ILuaTable config = engine.createTable();
        config.rawset("spawnX", 10);

        assertThrows(IllegalArgumentException.class, () -> service.create(DIMENSION_ID, config),
                "spawnX without spawnZ must be rejected");
    }
}
