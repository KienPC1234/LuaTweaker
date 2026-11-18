package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.worldgen.IWorldgenService;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

public class WorldgenLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull IWorldgenService service) {
        ILuaTable table = LuaBinder.bindTable(engine, service, IWorldgenService.class);
        if (service instanceof IWorldgenExtendedService) {
            ILuaTable extra = LuaBinder.bindTable(engine, service, IWorldgenExtendedService.class);
            extra.forEach((key, value) -> table.rawset(key.asString(), value));
        }
        engine.registerService("Worldgen", table);
        engine.getGlobalEnvironment().rawset("Worldgen", table);
        StubCatalog.register("Worldgen", IWorldgenService.class);
    }
}
