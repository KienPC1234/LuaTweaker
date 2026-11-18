package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Structures service works end-to-end through the real Lua VM:
 * global registration, PascalCase aliases, table config parsing and loud errors.
 */
public class StructureLuaBindingTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void binding_RegistersAndReadsBackThroughLua() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StructureServiceImpl service = new StructureServiceImpl();
        StructureLuaBinding.registerBindings(engine, service);

        engine.executeString(
            "Structures:Register('mymod:ancient_temple', {\n" +
            "    template = 'mymod:structures/temple',\n" +
            "    spawnBiomes = { 'minecraft:desert', 'minecraft:badlands' },\n" +
            "    spacing = 32,\n" +
            "    separation = 16,\n" +
            "    spawnChance = 0.8,\n" +
            "})\n" +
            "local summary = Structures:GetRegistrations()\n" +
            "assert(type(summary) == 'table', 'GetRegistrations must return a table')\n" +
            "assert(summary.structures == 1, 'expected 1 structure, got ' .. tostring(summary.structures))\n" +
            "local removed = Structures:Unregister('mymod:ancient_temple')\n" +
            "assert(removed == true, 'Unregister must return true')\n" +
            "assert(Structures:GetRegistrations().structures == 0, 'expected 0 after unregister')\n",
            "structures_binding_test"
        );

        Object registered = com.luatweaker.core.service.LuaServiceRegistry.get("StructureServiceImpl");
        assertTrue(registered instanceof StructureServiceImpl, "StructureServiceImpl must be in the service registry");
    }

    @Test
    void binding_InvalidConfigRaisesLuaError() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StructureLuaBinding.registerBindings(engine, new StructureServiceImpl());

        engine.executeString(
            "local ok1 = pcall(function() Structures:Register('', { template = 'm:structures/x' }) end)\n" +
            "assert(not ok1, 'blank structure id must raise a Lua error')\n" +
            "local ok2 = pcall(function() Structures:Register('m:x', {\n" +
            "    template = 'm:structures/x',\n" +
            "    spawnBiomes = { 'minecraft:plains' },\n" +
            "    spacing = 8, separation = 16 }) end)\n" +
            "assert(not ok2, 'spacing < separation must raise a Lua error')\n" +
            "local ok3 = pcall(function() Structures:Register('m:x', {\n" +
            "    template = 'm:structures/x',\n" +
            "    spawnBiomes = { 'minecraft:plains' },\n" +
            "    spawnChance = 2.0 }) end)\n" +
            "assert(not ok3, 'out-of-range spawnChance must raise a Lua error')\n",
            "structures_binding_error_test"
        );
    }

    @Test
    void binding_requireResolvesToGlobal() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StructureLuaBinding.registerBindings(engine, new StructureServiceImpl());

        engine.executeString(
            "local Structures = require('LuaTweaker.Structures')\n" +
            "assert(type(Structures) == 'table', 'require LuaTweaker.Structures failed')\n" +
            "assert(Structures.register ~= nil, 'register alias missing')\n",
            "structures_require_test"
        );
    }
}
