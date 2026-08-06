package com.luatweaker.spawn;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.api.vm.ILuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Lua checks of the SpawnRules service (handler-only API).
 */
public class SpawnRuleLuaBindingTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void binding_RegisterHandlerFromLua() {
        ILuaEngine engine = new CobaltLuaEngine();
        SpawnRuleLuaBinding.registerBindings(engine);

        engine.executeString(
            "SpawnRules:RegisterHandler('minecraft:test', function(dimensionId, players)\n" +
            "    local spawns = {}\n" +
            "    if #players > 0 then\n" +
            "        spawns[1] = { entity = 'minecraft:parrot', x = 10, y = 64, z = 10 }\n" +
            "    end\n" +
            "    return spawns\n" +
            "end)\n",
            "spawnrules_binding_test"
        );

        SpawnRuleServiceImpl service = (SpawnRuleServiceImpl)
                com.luatweaker.core.service.LuaServiceRegistry.get("SpawnRuleServiceImpl");
        assertNotNull(service);
        assertNotNull(service.getHandler("minecraft:test"), "handler must be registered from Lua");

        engine.executeString("SpawnRules:ClearHandler('minecraft:test')\n", "spawnrules_binding_clear");
        assertNull(service.getHandler("minecraft:test"), "handler must be cleared from Lua");
    }

    @Test
    void binding_RegisterRejectsMissingFunction() {
        ILuaEngine engine = new CobaltLuaEngine();
        SpawnRuleLuaBinding.registerBindings(engine);

        String script =
            "local ok = pcall(function()\n" +
            "    SpawnRules:RegisterHandler('minecraft:test')\n" +
            "end)\n" +
            "assert(not ok, 'RegisterHandler without a function must fail')\n" +
            "assert(SpawnRules:GetHandler('minecraft:test') == nil)\n";
        engine.executeString(script, "spawnrules_binding_reject_test");
    }

    @Test
    void binding_requireSpawnRulesResolvesToGlobal() {
        ILuaEngine engine = new CobaltLuaEngine();
        SpawnRuleLuaBinding.registerBindings(engine);
        engine.executeString(
            "local SpawnRules = require('LuaTweaker.SpawnRules')\n" +
            "assert(type(SpawnRules) == 'table' and SpawnRules.RegisterHandler ~= nil, 'require LuaTweaker.SpawnRules failed')\n" +
            "assert(SpawnRules.Register == nil, 'rule-based registration must be removed')\n" +
            "assert(SpawnRules.GetRules == nil, 'rule query API must be removed')\n",
            "spawnrules_require_test"
        );
    }
}
