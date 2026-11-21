package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies legacy per-column terrain generators: storage, Lua result parsing,
 * fallbacks and generation-version tracking.
 */
class DimensionTerrainGeneratorTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void terrainGeneratorStoresFunction() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setTerrainGenerator(DIMENSION_ID,
                luaFunction("__gen = function(x, z, baseHeight) return 80 end", "__gen"));

        DimensionServiceImpl.TerrainResult result = service.computeTerrain(DIMENSION_ID, 10, 20, 63);

        assertEquals(List.of(80, "minecraft:grass_block"),
                List.of(result.height(), result.blockId()),
                "a stored generator height must be used and the block must default to the surface block");
    }

    @Test
    void computeTerrainReturnsLuaBlockId() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setTerrainGenerator(DIMENSION_ID,
                luaFunction("__gen2 = function(x, z, baseHeight) return 120, 'arcane:crystal_peak' end",
                        "__gen2"));

        DimensionServiceImpl.TerrainResult result = service.computeTerrain(DIMENSION_ID, 0, 0, 63);

        assertEquals(List.of(120, "arcane:crystal_peak"),
                List.of(result.height(), result.blockId()),
                "computeTerrain must use both the height and block id returned by Lua");
    }

    @Test
    void computeTerrainFallsBackToBaseHeight() {
        service.create(DIMENSION_ID, engine.createTable());

        DimensionServiceImpl.TerrainResult result = service.computeTerrain(DIMENSION_ID, 5, 5, 70);

        assertEquals(List.of(70, "minecraft:grass_block"),
                List.of(result.height(), result.blockId()),
                "without a generator the base height and surface block must be used");
    }

    @Test
    void computeTerrainRejectsUnknownDimension() {
        assertThrows(IllegalArgumentException.class,
                () -> service.computeTerrain("minecraft:nope", 0, 0, 63),
                "computing terrain for an unknown dimension must be rejected");
    }

    @Test
    void computeTerrainIgnoresInvalidLuaHeight() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setTerrainGenerator(DIMENSION_ID,
                luaFunction("__bad = function(x, z, h) end", "__bad"));

        DimensionServiceImpl.TerrainResult result = service.computeTerrain(DIMENSION_ID, 1, 1, 55);

        assertEquals(55, result.height(),
                "a generator returning no height must fall back to the base height");
    }

    @Test
    void terrainGeneratorRejectsNonFunction() {
        for (Object candidate : List.of(engine.wrapNumber(5), "not a function")) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.setTerrainGenerator(DIMENSION_ID, candidate),
                    "terrain generator must be a Lua function: " + candidate);
        }
    }

    @Test
    void terrainGeneratorRejectsInvalidDimensionId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setTerrainGenerator("", engine.createTable()),
                "setting a terrain generator on an invalid dimension id must be rejected");
    }

    @Test
    void generationVersionBumpsOnCreate() {
        long before = service.getGenerationVersion();
        service.create(DIMENSION_ID, engine.createTable());

        assertTrue(service.getGenerationVersion() > before,
                "creating a dimension must bump the generation version");
    }

    @Test
    void generationVersionBumpsOnGeneratorChange() {
        service.create(DIMENSION_ID, engine.createTable());
        long before = service.getGenerationVersion();
        service.setTerrainGenerator(DIMENSION_ID,
                luaFunction("__gen3 = function(x, z, h) return 10 end", "__gen3"));

        assertTrue(service.getGenerationVersion() > before,
                "changing a terrain generator must bump the generation version");
    }

    private ILuaValue luaFunction(String script, String globalName) {
        engine.executeStringOrThrow(script, "terrain_generator_fixture");
        return engine.getGlobalEnvironment().rawget(globalName);
    }
}
