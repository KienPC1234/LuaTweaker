package com.luatweaker.loot;

import com.luatweaker.api.loot.ILootService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

public final class LootLuaBinding {

    private LootLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ILootService service) {
        LuaBinder.bind(engine, "Loot", service, ILootService.class);
        StubCatalog.register("Loot", ILootService.class);
    }
}
