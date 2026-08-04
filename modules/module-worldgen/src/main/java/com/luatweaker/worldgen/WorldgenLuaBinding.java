package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.worldgen.IWorldgenService;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public class WorldgenLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull IWorldgenService service) {
        LuaBinder.bind(engine, "Worldgen", service, IWorldgenService.class);
    }
}
