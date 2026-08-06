package com.luatweaker.dimension;

import com.luatweaker.api.biome.IBiomesService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public class BiomesLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine) {
        BiomesServiceImpl service = new BiomesServiceImpl(engine);
        LuaBinder.bind(engine, "Biomes", service, IBiomesService.class);
        engine.registerService("BiomesServiceImpl", service);
    }
}
