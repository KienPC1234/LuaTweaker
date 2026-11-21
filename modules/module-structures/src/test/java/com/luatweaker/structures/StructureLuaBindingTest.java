package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Structures service works end-to-end through the real Lua VM:
 * global registration, PascalCase aliases, table config parsing and loud errors.
 */
class StructureLuaBindingTest {

    private static final String SERVICE_NAME = "StructureServiceImpl";

    private static final String ROUND_TRIP_SCRIPT = """
        Structures:Register('mymod:ancient_temple', {
            template = 'mymod:structures/temple',
            spawnBiomes = { 'minecraft:desert', 'minecraft:badlands' },
            spacing = 32,
            separation = 16,
            spawnChance = 0.8,
        })
        local summary = Structures:GetRegistrations()
        assert(type(summary) == 'table', 'GetRegistrations must return a table')
        assert(summary.structures == 1, 'expected 1 structure, got ' .. tostring(summary.structures))
        local removed = Structures:Unregister('mymod:ancient_temple')
        assert(removed == true, 'Unregister must return true')
        assert(Structures:GetRegistrations().structures == 0, 'expected 0 after unregister')
        """;

    private static final String INVALID_CONFIG_SCRIPT = """
        local ok1 = pcall(function() Structures:Register('', { template = 'm:structures/x' }) end)
        assert(not ok1, 'blank structure id must raise a Lua error')
        local ok2 = pcall(function() Structures:Register('m:x', {
            template = 'm:structures/x',
            spawnBiomes = { 'minecraft:plains' },
            spacing = 8, separation = 16 }) end)
        assert(not ok2, 'spacing < separation must raise a Lua error')
        local ok3 = pcall(function() Structures:Register('m:x', {
            template = 'm:structures/x',
            spawnBiomes = { 'minecraft:plains' },
            spawnChance = 2.0 }) end)
        assert(not ok3, 'out-of-range spawnChance must raise a Lua error')
        """;

    private static final String REQUIRE_SCRIPT = """
        local Structures = require('LuaTweaker.Structures')
        assert(type(Structures) == 'table', 'require LuaTweaker.Structures failed')
        assert(Structures.register ~= nil, 'register alias missing')
        """;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void bindingRegistersServiceInRegistry() {
        assertTrue(bindAndRegister() instanceof StructureServiceImpl,
            "StructureServiceImpl must be reachable through LuaServiceRegistry after binding");
    }

    @Test
    void bindingRoundTripRunsWithoutError() {
        assertDoesNotThrow(StructureLuaBindingTest::bindAndRegister,
            "register, summarize and unregister must complete without a Lua error");
    }

    @Test
    void bindingRejectsInvalidConfigWithLuaError() {
        assertDoesNotThrow(StructureLuaBindingTest::runInvalidConfig,
            "every invalid config must be caught by pcall so no Lua error escapes");
    }

    @Test
    void bindingRequireResolvesServiceGlobal() {
        assertDoesNotThrow(StructureLuaBindingTest::runRequire,
            "require('LuaTweaker.Structures') must resolve the service global");
    }

    private static Object bindAndRegister() {
        newBoundEngine().executeString(ROUND_TRIP_SCRIPT, "structures_binding_test");
        return LuaServiceRegistry.get(SERVICE_NAME);
    }

    private static void runInvalidConfig() {
        newBoundEngine().executeString(INVALID_CONFIG_SCRIPT, "structures_binding_error_test");
    }

    private static void runRequire() {
        newBoundEngine().executeString(REQUIRE_SCRIPT, "structures_require_test");
    }

    private static ILuaEngine newBoundEngine() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        StructureLuaBinding.registerBindings(engine, new StructureServiceImpl());
        return engine;
    }
}
