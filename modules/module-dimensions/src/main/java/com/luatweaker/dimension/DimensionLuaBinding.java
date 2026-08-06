package com.luatweaker.dimension;

import com.luatweaker.api.dimension.IDimensionService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public class DimensionLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine) {
        DimensionServiceImpl service = new DimensionServiceImpl(engine);
        LuaBinder.bind(engine, "Dimensions", service, IDimensionService.class);
        engine.registerService("DimensionServiceImpl", service);
    }
}
