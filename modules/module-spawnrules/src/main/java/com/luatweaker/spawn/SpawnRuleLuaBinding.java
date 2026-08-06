package com.luatweaker.spawn;

import com.luatweaker.api.spawn.ISpawnRuleService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public class SpawnRuleLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine) {
        SpawnRuleServiceImpl service = new SpawnRuleServiceImpl(engine);
        LuaBinder.bind(engine, "SpawnRules", service, ISpawnRuleService.class);
        engine.registerService("SpawnRuleServiceImpl", service);
    }
}
