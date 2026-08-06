package com.luatweaker.dimension;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
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
        ILuaEngine engine = new CobaltLuaEngine();
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
        ILuaEngine engine = new CobaltLuaEngine();
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
        ILuaEngine engine = new CobaltLuaEngine();
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
        ILuaEngine engine = new CobaltLuaEngine();
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
        ILuaEngine engine = new CobaltLuaEngine();
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
        ILuaEngine engine = new CobaltLuaEngine();
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "local Dimensions = require('LuaTweaker.Dimensions')\n" +
            "assert(type(Dimensions) == 'table' and Dimensions.Create ~= nil, 'require LuaTweaker.Dimensions failed')\n",
            "dimension_require_test"
        );
    }
}
