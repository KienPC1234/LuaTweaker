package com.luatweaker.recipes;

import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

public final class RecipesLuaBinding {

    private RecipesLuaBinding() {
    }

    public static void bind(ILuaTable table, IRecipeManagerService service) {
        bind(null, table, service);
    }

    public static void bind(ILuaEngine engine, ILuaTable table, IRecipeManagerService service) {
        RecipeRemovalBinding.bindRemovals(table, service);
        RecipeRemovalBinding.bindReplacements(table, service);
        RecipeAdditionBinding.bindShapedRecipes(table, service);
        RecipeAdditionBinding.bindCookingRecipes(table, service);
        RecipeAdditionBinding.bindStoneSmithingRecipes(table, service);
        RecipeAdditionBinding.bindAnvilBrewingTradeRecipes(table, service);
        aliasRecipeMethods(table);
        RecipeBuilderBinding.bindRecipeBuilders(engine, table, service);
    }

    private static void aliasRecipeMethods(ILuaTable table) {
        // PascalCase aliases for table methods
        for (String method : new String[]{
                "removeByOutput", "removeByInput", "removeById", "removeByMod", "removeByTag", "removeAll",
                "addShaped", "replaceInput", "replaceOutput", "addSmelting", "addBlasting", "addSmoking",
                "addCampfire", "addShapeless", "addStonecutting", "addSmithing", "addAnvil", "addBrewing", "addTrade"
        }) {
            ILuaValue fn = table.rawget(method);
            if (fn != null && fn.isFunction()) {
                table.rawset(Character.toUpperCase(method.charAt(0)) + method.substring(1), fn);
            }
        }
    }
}
