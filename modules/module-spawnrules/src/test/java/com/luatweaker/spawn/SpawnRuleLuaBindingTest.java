package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End-to-end Lua checks of the SpawnRules service (handler-only API).
 */
class SpawnRuleLuaBindingTest {

    private static final String TEST_DIMENSION = "minecraft:test";
    private static final String REGISTER_SCRIPT =
            "SpawnRules:RegisterHandler('minecraft:test', function(dimensionId, players)\n"
            + "    local spawns = {}\n"
            + "    if #players > 0 then\n"
            + "        spawns[1] = { entity = 'minecraft:parrot', x = 10, y = 64, z = 10 }\n"
            + "    end\n"
            + "    return spawns\n"
            + "end)\n";

    private ILuaEngine engine;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        SpawnRuleLuaBinding.registerBindings(engine);
    }

    @Test
    void bindingRegistersTheServiceInTheRegistry() {
        assertNotNull(LuaServiceRegistry.get("SpawnRuleServiceImpl"),
                "SpawnRuleLuaBinding.registerBindings must register SpawnRuleServiceImpl");
    }

    @Test
    void registerHandlerFromLuaStoresTheHandler() {
        engine.executeString(REGISTER_SCRIPT, "spawnrules_binding_test");

        assertNotNull(service().getHandler(TEST_DIMENSION),
                "a handler registered from Lua must be visible to the Java service");
    }

    @Test
    void clearHandlerFromLuaRemovesTheHandler() {
        engine.executeString(REGISTER_SCRIPT, "spawnrules_binding_test");
        engine.executeString("SpawnRules:ClearHandler('minecraft:test')\n", "spawnrules_binding_clear");

        assertNull(service().getHandler(TEST_DIMENSION),
                "a handler cleared from Lua must be removed from the Java service");
    }

    @Test
    void registerHandlerRejectsAMissingFunction() {
        engine.executeString(
            "local ok = pcall(function()\n"
            + "    SpawnRules:RegisterHandler('minecraft:test')\n"
            + "end)\n"
            + "assert(not ok, 'RegisterHandler without a function must fail')\n",
            "spawnrules_binding_reject_test");

        Assertions.assertAll("a rejected registration must fail loudly and store nothing",
            () -> assertNull(engine.getAndClearLastExecutionError(),
                "the Lua-side pcall check for a missing handler must pass"),
            () -> assertNull(service().getHandler(TEST_DIMENSION),
                "the rejected registration must not leave a handler behind"));
    }

    @Test
    void requireSpawnRulesResolvesToTheGlobalTable() {
        engine.executeString(
            "local SpawnRules = require('LuaTweaker.SpawnRules')\n"
            + "assert(type(SpawnRules) == 'table' and SpawnRules.RegisterHandler ~= nil, "
            + "'require LuaTweaker.SpawnRules failed')\n"
            + "assert(SpawnRules.Register == nil, 'rule-based registration must be removed')\n"
            + "local rules = SpawnRules:GetRules('minecraft:test')\n"
            + "assert(type(rules) == 'table' and #rules == 0, 'no declarative rules must be registered')\n",
            "spawnrules_require_test");

        assertNull(engine.getAndClearLastExecutionError(),
                "require('LuaTweaker.SpawnRules') must expose the handler API without errors");
    }

    private SpawnRuleServiceImpl service() {
        return (SpawnRuleServiceImpl) LuaServiceRegistry.get("SpawnRuleServiceImpl");
    }
}
