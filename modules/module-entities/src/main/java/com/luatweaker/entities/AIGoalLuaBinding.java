package com.luatweaker.entities;

import com.luatweaker.api.entity.ai.IAIGoalService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public class AIGoalLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine) {
        LuaBinder.bind(engine, "AIGoals", new AIGoalServiceImpl(engine), IAIGoalService.class);
    }
}
