package com.luatweaker.dimension;

import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.api.vm.ILuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end checks of the Dimensions service through the real Lua VM:
 * create with config, terrain generator callback, portals and info tables.
 */
public class DimensionLuaBindingTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void binding_CreateAndInspectDimensionFromLua() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('arcane:crystal_realm', {\n" +
            "    hasSkyLight = true,\n" +
            "    skyColor = 0xFF88FF,\n" +
            "    seaLevel = 63,\n" +
            "    surfaceBlock = 'arcane:crystal_grass',\n" +
            "    biomes = {\n" +
            "        { id = 'arcane:crystal_plains', weight = 5 },\n" +
            "        { id = 'minecraft:plains' }\n" +
            "    },\n" +
            "    spawnEntities = {\n" +
            "        { entity = 'minecraft:zombie', weight = 1, minGroup = 1, maxGroup = 2 }\n" +
            "    }\n" +
            "})\n" +
            "local info = Dimensions:GetDimension('arcane:crystal_realm')\n" +
            "assert(info ~= nil, 'getDimension must return a table')\n" +
            "assert(info.id == 'arcane:crystal_realm')\n" +
            "assert(info.skyColor == 0xFF88FF)\n" +
            "assert(info.surfaceBlock == 'arcane:crystal_grass')\n" +
            "assert(#info.biomes == 2 and info.biomes[1].weight == 5)\n" +
            "assert(#info.spawnEntities == 1 and info.spawnEntities[1].entity == 'minecraft:zombie')\n" +
            "assert(Dimensions:GetDimension('minecraft:unknown') == nil, 'unknown dimension must return nil')\n",
            "dimension_binding_test"
        );

        assertTrue(engine.getGlobalEnvironment().rawget("Dimensions").isTable(),
                "Dimensions global must be registered");
    }

    @Test
    void binding_TerrainGeneratorReturnsHeightAndBlock() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('minecraft:test', { surfaceBlock = 'minecraft:grass_block' })\n" +
            "Dimensions:SetTerrainGenerator('minecraft:test', function(x, z, baseHeight)\n" +
            "    return baseHeight + 10, 'minecraft:stone'\n" +
            "end)\n",
            "dimension_binding_terrain_test"
        );

        DimensionServiceImpl service = (DimensionServiceImpl)
                com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        assertNotNull(service);
        DimensionServiceImpl.TerrainResult result = service.computeTerrain("minecraft:test", 3, 4, 63);
        assertEquals(73, result.height());
        assertEquals("minecraft:stone", result.blockId());
    }

    @Test
    void binding_PortalRegistration() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('arcane:crystal_realm', {})\n" +
            "Dimensions:RegisterPortal('arcane:crystal_portal', 'arcane:crystal_realm')\n" +
            "local info = Dimensions:GetDimension('arcane:crystal_realm')\n" +
            "assert(info.portals['arcane:crystal_portal'] == 'arcane:crystal_realm')\n",
            "dimension_binding_portal_test"
        );
    }

    @Test
    void binding_InvalidConfigRaisesLuaError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "local ok, err = pcall(function()\n" +
            "    Dimensions:Create('minecraft:bad', { minHeight = 100, maxHeight = 50 })\n" +
            "end)\n" +
            "assert(not ok, 'invalid height range must raise a Lua error')\n",
            "dimension_binding_error_test"
        );
    }

    @Test
    void binding_BlockPickerSpawnPointAndPortalTarget() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('arcane:crystal_realm', { surfaceBlock = 'arcane:crystal_grass' })\n" +
            "Dimensions:SetBlockPicker('arcane:crystal_realm', function(x, z, surfaceY, minY)\n" +
            "    local o = {}\n" +
            "    if x % 4 == 0 then\n" +
            "        o[surfaceY - 3] = 'minecraft:air'\n" +
            "    end\n" +
            "    return o\n" +
            "end)\n" +
            "Dimensions:SetSpawnPoint('arcane:crystal_realm', 42, -42)\n" +
            "Dimensions:RegisterPortal('arcane:portal', 'arcane:crystal_realm')\n" +
            "local info = Dimensions:GetDimension('arcane:crystal_realm')\n" +
            "assert(info.hasBlockPicker == true, 'info must expose hasBlockPicker')\n" +
            "assert(info.spawnX == 42 and info.spawnZ == -42, 'info must expose the spawn point')\n" +
            "assert(Dimensions:GetPortalTarget('arcane:portal') == 'arcane:crystal_realm')\n" +
            "assert(Dimensions:GetPortalTarget('arcane:nope') == nil)\n",
            "dimension_binding_picker_test"
        );

        DimensionServiceImpl service = (DimensionServiceImpl)
                com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        Map<Integer, String> overrides = service.computeBlockOverrides(
                "arcane:crystal_realm", 4, 0, 80, -64, 320);
        assertEquals(1, overrides.size());
        assertEquals("minecraft:air", overrides.get(77));
        assertTrue(service.computeBlockOverrides("arcane:crystal_realm", 1, 0, 80, -64, 320).isEmpty());
    }

    @Test
    void binding_requireDimensionsResolvesToGlobal() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "local Dimensions = require('LuaTweaker.Dimensions')\n" +
            "assert(type(Dimensions) == 'table' and Dimensions.Create ~= nil, 'require LuaTweaker.Dimensions failed')\n",
            "dimension_require_test"
        );
    }

    @Test
    void binding_BatchTerrainAndBiomesRunOncePerChunk() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeStringOrThrow(
            "batchTerrainCalls = 0\n" +
            "Dimensions:Create('minecraft:batch_dim', { seaLevel = 64 })\n" +
            "Dimensions:SetChunkTerrain('minecraft:batch_dim', function(cx, cz, ctx)\n" +
            "    batchTerrainCalls = batchTerrainCalls + 1\n" +
            "    local heights, surfaces = {}, {}\n" +
            "    for z = 0, 15 do\n" +
            "        for x = 0, 15 do\n" +
            "            local i = z * 16 + x + 1\n" +
            "            heights[i] = ctx.seaLevel + x + z\n" +
            "            surfaces[i] = 'minecraft:stone'\n" +
            "        end\n" +
            "    end\n" +
            "    return heights, surfaces, { [1] = { [80] = 'minecraft:gold_block' } }\n" +
            "end)\n" +
            "Dimensions:SetBiomeBatch('minecraft:batch_dim', function(cx, cz, ctx)\n" +
            "    local t = {}\n" +
            "    for i = 1, 16 do t[i] = 'minecraft:plains' end\n" +
            "    t[6] = 'minecraft:ocean'\n" +
            "    return t\n" +
            "end)\n" +
            "local info = Dimensions:GetDimension('minecraft:batch_dim')\n" +
            "assert(info.terrainMode == 'chunk', 'info must report chunk terrain mode')\n" +
            "assert(info.hasChunkTerrain == true and info.hasBiomeBatch == true)\n",
            "dimension_batch_binding_test"
        );

        DimensionServiceImpl service = (DimensionServiceImpl)
                com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        DimensionServiceImpl.ChunkTerrainResult result =
                service.computeChunkTerrain("minecraft:batch_dim", 2, 3);

        assertEquals(1, engine.getGlobalEnvironment().rawget("batchTerrainCalls").asInt());
        assertEquals(64, result.heights()[0]);
        assertEquals(65, result.heights()[1]);
        assertEquals("minecraft:stone", result.surfaces()[0]);
        assertEquals("minecraft:gold_block", result.overrides().get(0).get(80));

        String[] biomes = service.computeBiomeBatch("minecraft:batch_dim", 2, 3);
        assertEquals(16, biomes.length);
        assertEquals("minecraft:ocean", biomes[5]);
        assertEquals("minecraft:plains", biomes[0]);
    }

    @Test
    void binding_TerrainSpecRunsNatively() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);
        com.luatweaker.noise.NoiseLuaBinding.registerBindings(engine);

        engine.executeStringOrThrow(
            "Dimensions:Create('minecraft:spec_dim', { seaLevel = 63 })\n" +
            "Dimensions:SetTerrainSpec('minecraft:spec_dim', {\n" +
            "    height = { { kind = 'fbm', amp = 20, frequency = 0.01 } },\n" +
            "    spline = { { -1, 40 }, { 1, 200 } },\n" +
            "    surface = { { block = 'minecraft:stone', maxDepth = 3 } },\n" +
            "})\n" +
            "local info = Dimensions:GetDimension('minecraft:spec_dim')\n" +
            "assert(info.terrainMode == 'spec', 'info must report spec terrain mode')\n" +
            "assert(info.hasTerrainSpec == true, 'info must report hasTerrainSpec')\n",
            "dimension_spec_binding_test");

        DimensionServiceImpl service = (DimensionServiceImpl)
                com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        TerrainSampler sampler = service.createTerrainSampler("minecraft:spec_dim");
        assertNotNull(sampler);

        int[] heights = new int[256];
        float[] slopes = new float[256];
        sampler.sampleChunk(0, 0, heights, slopes);
        for (int height : heights) {
            assertTrue(height >= 40 && height <= 200,
                    "spline-mapped height out of range: " + height);
        }
        assertEquals("minecraft:stone", sampler.surfaceBlockAt(0, 120, 0.0));
    }
}
