package com.luatweaker.interception;

import com.luatweaker.api.interception.IInterceptionService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public final class InterceptionLuaBinding {
    private static final int DEFAULT_ANVIL_EXP_COST = 1;
    private static final int DEFAULT_TRADE_MAX_USES = 16;
    private static final int DEFAULT_TRADE_XP = 2;
    private static final int MIN_ANVIL_ARGS = 4;
    private static final int MIN_BREWING_ARGS = 4;
    private static final int MIN_TRADE_ARGS = 4;

    private InterceptionLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull IInterceptionService interceptionService) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaTable table = engine.createTable();

        table.rawset("AddAnvilRecipe", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ANVIL_ARGS) {
                throw new IllegalArgumentException(
                    "AddAnvilRecipe requires recipeId, leftInput, rightInput, outputItem");
            }
            interceptionService.addAnvilRecipe(
                args[off].asString(),
                args[off + 1].asString(),
                args[off + 2].asString(),
                args[off + 3].asString(),
                args.length - off >= 5 ? args[off + 4].asInt() : DEFAULT_ANVIL_EXP_COST
            );
            return engine.nilValue();
        });

        table.rawset("AddBrewingRecipe", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_BREWING_ARGS) {
                throw new IllegalArgumentException(
                    "AddBrewingRecipe requires recipeId, inputPotion, catalystItem, outputPotion");
            }
            interceptionService.addBrewingRecipe(
                args[off].asString(),
                args[off + 1].asString(),
                args[off + 2].asString(),
                args[off + 3].asString()
            );
            return engine.nilValue();
        });

        table.rawset("AddVillagerTrade", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_TRADE_ARGS) {
                throw new IllegalArgumentException(
                    "AddVillagerTrade requires profession, level, buyItem, sellItem");
            }
            interceptionService.addVillagerTrade(
                args[off].asString(),
                args[off + 1].asInt(),
                args[off + 2].asString(),
                args[off + 3].asString(),
                args.length - off >= 5 ? args[off + 4].asInt() : DEFAULT_TRADE_MAX_USES,
                args.length - off >= 6 ? args[off + 5].asInt() : DEFAULT_TRADE_XP
            );
            return engine.nilValue();
        });

        table.rawset("GetRegistrationCount", args -> engine.wrapNumber(interceptionService.getRegistrationCount()));

        globals.rawset("Interception", table);
        engine.registerService("InterceptionService", table);
    }
}
