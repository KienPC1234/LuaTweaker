package com.luatweaker.structures;

import com.luatweaker.api.structures.IStructureService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

public class StructureLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull IStructureService service) {
        LuaBinder.bind(engine, "Structures", service, IStructureService.class);
        engine.registerService("StructureServiceImpl", service);
        StubCatalog.register("Structures", IStructureService.class);
    }
}
