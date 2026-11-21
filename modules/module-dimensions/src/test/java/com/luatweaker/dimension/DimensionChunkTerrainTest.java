package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the batch chunk-terrain callback: one Lua call per chunk, index layout,
 * surface/override parsing and loud failure on malformed output.
 */
class DimensionChunkTerrainTest extends DimensionServiceTestBase {

    private static final String LOCAL_HEIGHTS = "    local heights = {}\n";
    private static final String RETURN_HEIGHTS = "    return heights\n";
    private static final String FILL_HEIGHTS_64 = "    for i = 1, 256 do heights[i] = 64 end\n";
    private static final String END = "end";

    private static final String DIMENSION_ID = "minecraft:batch";

    private record OverrideView(Map<Integer, String> firstColumn, boolean outOfRangeColumn) {
    }

    @Test
    void chunkTerrainCallbackRunsOncePerChunk() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "chunkTerrainCalls = 0\n"
                        + "function batchChunkTerrain(cx, cz, ctx)\n"
                        + "    chunkTerrainCalls = chunkTerrainCalls + 1\n"
                        + LOCAL_HEIGHTS
                        + "    for i = 1, 256 do heights[i] = ctx.seaLevel end\n"
                        + RETURN_HEIGHTS
                        + END, "batchChunkTerrain"));

        service.computeChunkTerrain(DIMENSION_ID, 4, -7);

        assertEquals(1, engine.getGlobalEnvironment().rawget("chunkTerrainCalls").asInt(),
                "the batch terrain callback must run exactly once per chunk");
    }

    @Test
    void chunkTerrainFillsHeightsWithXFastest() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function heightGrid(cx, cz, ctx)\n"
                        + LOCAL_HEIGHTS
                        + "    for z = 0, 15 do\n"
                        + "        for x = 0, 15 do\n"
                        + "            heights[z * 16 + x + 1] = ctx.seaLevel + x + z\n"
                        + "        end\n"
                        + "    end\n"
                        + RETURN_HEIGHTS
                        + END, "heightGrid"));

        DimensionServiceImpl.ChunkTerrainResult result =
                service.computeChunkTerrain(DIMENSION_ID, 4, -7);

        assertEquals(List.of(63, 64, 64, DimensionServiceImpl.CHUNK_COLUMNS),
                List.of(result.heights()[0], result.heights()[1], result.heights()[16],
                        result.heights().length),
                "chunk heights must fill one 16x16 grid with x as the fastest index");
    }

    @Test
    void chunkTerrainSurfaceFallsBackToConfig() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function surfaceGrid(cx, cz, ctx)\n"
                        + "    local heights, surfaces = {}, {}\n"
                        + FILL_HEIGHTS_64
                        + "    surfaces[1] = 'minecraft:stone'\n"
                        + "    return heights, surfaces\n"
                        + END, "surfaceGrid"));

        DimensionServiceImpl.ChunkTerrainResult result =
                service.computeChunkTerrain(DIMENSION_ID, 0, 0);

        assertEquals(List.of("minecraft:stone", "minecraft:grass_block"),
                List.of(result.surfaces()[0], result.surfaces()[1]),
                "a nil surface must fall back to the config surface block");
    }

    @Test
    void chunkTerrainDropsOutOfRangeOverrides() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function overrideGrid(cx, cz, ctx)\n"
                        + LOCAL_HEIGHTS
                        + FILL_HEIGHTS_64
                        + "    return heights, nil, {\n"
                        + "        [1] = { [0] = 'minecraft:air', [20] = 'minecraft:gold_block',"
                        + " [4000] = 'minecraft:air' },\n"
                        + "        [300] = { [0] = 'minecraft:air' },\n"
                        + "    }\n"
                        + END, "overrideGrid"));

        DimensionServiceImpl.ChunkTerrainResult result =
                service.computeChunkTerrain(DIMENSION_ID, 0, 0);

        assertEquals(new OverrideView(Map.of(0, "minecraft:air", 20, "minecraft:gold_block"), false),
                new OverrideView(result.overrides().get(0), result.overrides().containsKey(299)),
                "out-of-range Y values and columns must be dropped from chunk overrides");
    }

    @Test
    void chunkTerrainReportsChunkMode() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function flatGrid(cx, cz, ctx)\n"
                        + LOCAL_HEIGHTS
                        + FILL_HEIGHTS_64
                        + RETURN_HEIGHTS
                        + END, "flatGrid"));

        assertEquals(List.of(DimensionServiceImpl.TerrainMode.CHUNK, true),
                List.of(service.getTerrainMode(DIMENSION_ID), service.hasChunkTerrain(DIMENSION_ID)),
                "installing a chunk terrain callback must switch the dimension to chunk mode");
    }

    @Test
    void chunkTerrainMissingHeightsFailsLoudly() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function brokenChunk(cx, cz, ctx) return nil end", "brokenChunk"));

        assertTrue(DimensionExceptions.illegalStateMessage(
                        () -> service.computeChunkTerrain(DIMENSION_ID, 0, 0))
                        .contains("heights table"),
                "a callback without a heights table must abort chunk generation loudly");
    }

    @Test
    void chunkTerrainSparseColumnsBecomeVoid() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function sparseChunk(cx, cz, ctx)\n"
                        + LOCAL_HEIGHTS
                        + "    heights[1] = ctx.minY\n"
                        + "    heights[2] = 'not a number'\n"
                        + RETURN_HEIGHTS
                        + END, "sparseChunk"));

        DimensionServiceImpl.ChunkTerrainResult result =
                service.computeChunkTerrain(DIMENSION_ID, 0, 0);
        int voidHeight = DimensionConfig.DEFAULT_MIN_HEIGHT - 1;

        assertEquals(List.of(DimensionConfig.DEFAULT_MIN_HEIGHT, voidHeight, voidHeight),
                List.of(result.heights()[0], result.heights()[1], result.heights()[200]),
                "missing or non-numeric heights must become void columns");
    }

    @Test
    void chunkTerrainInvalidSurfaceFallsBack() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function badSurfaceChunk(cx, cz, ctx)\n"
                        + "    local heights, surfaces = {}, {}\n"
                        + FILL_HEIGHTS_64
                        + "    surfaces[1] = 'Not A Block'\n"
                        + "    return heights, surfaces\n"
                        + END, "badSurfaceChunk"));

        DimensionServiceImpl.ChunkTerrainResult result =
                service.computeChunkTerrain(DIMENSION_ID, 0, 0);

        assertEquals("minecraft:grass_block", result.surfaces()[0],
                "an invalid surface block must fall back to the config surface block");
    }

    @Test
    void chunkTerrainOverridesAboveCapFailLoudly() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setChunkTerrain(DIMENSION_ID, luaFunction(
                "function overridesFor(cx, cz, ctx)\n"
                        + LOCAL_HEIGHTS
                        + "    for i = 1, 256 do heights[i] = 0 end\n"
                        + "    return heights, nil, {\n"
                        + "        [1] = { [0] = 'minecraft:stone', [1] = 'minecraft:stone',"
                        + " [2] = 'minecraft:stone' },\n"
                        + "    }\n"
                        + END, "overridesFor"));
        ILuaValue callback = engine.getGlobalEnvironment().rawget("overridesFor");
        ILuaTable context = engine.createTable();
        context.rawset("seaLevel", 63.0);
        context.rawset("minY", -64.0);
        context.rawset("maxY", 320.0);
        ILuaValue[] results = engine.callFunctionMulti(callback,
                engine.wrapNumber(0), engine.wrapNumber(0), context);

        assertTrue(DimensionExceptions.illegalStateMessage(
                        () -> service.parseChunkOverrides(DIMENSION_ID, 0, 0, results, -64, 320, 2))
                        .contains("block overrides"),
                "exceeding the chunk override cap must fail loudly");
    }

    private ILuaValue luaFunction(String script, String globalName) {
        engine.executeStringOrThrow(script, "chunk_terrain_fixture");
        return engine.getGlobalEnvironment().rawget(globalName);
    }
}
