package com.luatweaker.dimension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end checks of dimension creation, portal registration, error reporting and
 * {@code require("LuaTweaker.Dimensions")} through the real Lua VM.
 */
class DimensionLuaBindingTest extends DimensionLuaTestBase {

    private static final String QUOTE_CLOSE = "')\n";

    @Test
    void createAndInspectDimensionFromLua() {
        engine.executeStringOrThrow(
                "Dimensions:Create('" + REALM + "', {\n"
                        + "    hasSkyLight = true,\n"
                        + "    skyColor = 0xFF88FF,\n"
                        + "    seaLevel = 63,\n"
                        + "    surfaceBlock = 'arcane:crystal_grass',\n"
                        + "    biomes = {\n"
                        + "        { id = 'arcane:crystal_plains', weight = 5 },\n"
                        + "        { id = 'minecraft:plains' }\n"
                        + "    },\n"
                        + "    spawnEntities = {\n"
                        + "        { entity = 'minecraft:zombie', weight = 1, minGroup = 1, maxGroup = 2 }\n"
                        + "    }\n"
                        + "})\n"
                        + "local info = Dimensions:GetDimension('" + REALM + QUOTE_CLOSE
                        + "assert(info ~= nil, 'getDimension must return a table')\n"
                        + "assert(info.id == '" + REALM + QUOTE_CLOSE
                        + "assert(info.skyColor == 0xFF88FF)\n"
                        + "assert(info.surfaceBlock == 'arcane:crystal_grass')\n"
                        + "assert(#info.biomes == 2 and info.biomes[1].weight == 5)\n"
                        + "assert(#info.spawnEntities == 1"
                        + " and info.spawnEntities[1].entity == 'minecraft:zombie')\n"
                        + "assert(Dimensions:GetDimension('minecraft:unknown') == nil,"
                        + " 'unknown dimension must return nil')\n",
                "dimension_binding_test");

        assertTrue(engine.getGlobalEnvironment().rawget("Dimensions").isTable(),
                "Dimensions global must be registered");
    }

    @Test
    void portalRegistration() {
        engine.executeStringOrThrow(
                "Dimensions:Create('" + REALM + "', {})\n"
                        + "Dimensions:RegisterPortal('" + PORTAL + "', '" + REALM + QUOTE_CLOSE
                        + "local info = Dimensions:GetDimension('" + REALM + QUOTE_CLOSE
                        + "assert(info.portals['" + PORTAL + "'] == '" + REALM + QUOTE_CLOSE,
                "dimension_binding_portal_test");

        assertEquals(REALM, dimensionService().getPortalTarget(PORTAL),
                "the portal registered from Lua must resolve to its target dimension");
    }

    @Test
    void invalidConfigRaisesLuaError() {
        engine.executeStringOrThrow(
                "local ok, err = pcall(function()\n"
                        + "    Dimensions:Create('minecraft:bad',"
                        + " { minHeight = 100, maxHeight = 50 })\n"
                        + "end)\n"
                        + "assert(not ok, 'invalid height range must raise a Lua error')\n",
                "dimension_binding_error_test");

        assertNull(dimensionService().getDimension("minecraft:bad"),
                "a dimension rejected by Lua must not be registered");
    }

    @Test
    void requireDimensionsResolvesToGlobal() {
        engine.executeStringOrThrow(
                "local Dimensions = require('LuaTweaker.Dimensions')\n"
                        + "assert(type(Dimensions) == 'table' and Dimensions.Create ~= nil,"
                        + " 'require LuaTweaker.Dimensions failed')\n",
                "dimension_require_test");

        assertTrue(engine.getGlobalEnvironment().rawget("Dimensions").isTable(),
                "require('LuaTweaker.Dimensions') must resolve to the Dimensions global table");
    }
}
