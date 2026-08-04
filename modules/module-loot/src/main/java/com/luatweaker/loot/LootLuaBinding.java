package com.luatweaker.loot;

import com.luatweaker.api.loot.ILootService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public class LootLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ILootService service) {
        LuaBinder.bind(engine, "Loot", service, ILootService.class);
    }
}
