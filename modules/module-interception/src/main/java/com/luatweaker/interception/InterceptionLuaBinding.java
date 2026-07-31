package com.luatweaker.interception;

import com.luatweaker.api.interception.IInterceptionService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

public class InterceptionLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull IInterceptionService interceptionService) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaTable table = engine.createTable();

        table.rawset("AddAnvilRecipe", args -> {
            if (args.length >= 5) {
                interceptionService.addAnvilRecipe(
                    args[1].asString(),
                    args[2].asString(),
                    args[3].asString(),
                    args[4].asString(),
                    args.length >= 6 ? args[5].asInt() : 1
                );
            }
            return engine.nilValue();
        });

        table.rawset("AddBrewingRecipe", args -> {
            if (args.length >= 5) {
                interceptionService.addBrewingRecipe(
                    args[1].asString(),
                    args[2].asString(),
                    args[3].asString(),
                    args[4].asString()
                );
            }
            return engine.nilValue();
        });

        table.rawset("AddVillagerTrade", args -> {
            if (args.length >= 6) {
                interceptionService.addVillagerTrade(
                    args[1].asString(),
                    args[2].asInt(),
                    args[3].asString(),
                    args[4].asString(),
                    args.length >= 6 ? args[5].asInt() : 16,
                    args.length >= 7 ? args[6].asInt() : 2
                );
            }
            return engine.nilValue();
        });

        table.rawset("ClearInterceptions", args -> {
            interceptionService.clearPendingInterceptions();
            return engine.nilValue();
        });

        globals.rawset("Interception", table);
        engine.registerService("InterceptionService", interceptionService);
    }
}
