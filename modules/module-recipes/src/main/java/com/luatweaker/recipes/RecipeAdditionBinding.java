package com.luatweaker.recipes;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.bind.LuaBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Binds direct recipe addition operations (shaped/shapeless/cooking/specialty). */
final class RecipeAdditionBinding {

    private static final String TIME_DETAIL_SEPARATOR = ", time: ";

    private RecipeAdditionBinding() {
    }

    static void bindShapedRecipes(ILuaTable table, IRecipeManagerService service) {
        // recipes:addShaped("ruby_sword", item("minecraft:diamond_sword"), {" R ", " R ", " S "}, { R = ingredient(...) })
        table.rawset("addShaped", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 4)) {
                throw new IllegalArgumentException("Missing arguments for addShaped");
            }
            String recipeId = args[off].asString();
            ItemCount output = RecipeParse.parseItemCount(args[off + 1], RecipeBindingSupport.ARG_OUTPUT);

            List<String> pattern = new ArrayList<>();
            ILuaTable patTable = args[off + 2].asTable();
            int patLen = patTable.length();
            for (int i = 1; i <= patLen; i++) {
                pattern.add(patTable.rawget(i).asString());
            }

            Map<String, IngredientWrapper> keys = new ConcurrentHashMap<>();
            ILuaTable keysTable = args[off + 3].asTable();
            for (Map.Entry<ILuaValue, ILuaValue> entry : keysTable.asMap().entrySet()) {
                String keyChar = entry.getKey().asString();
                IngredientWrapper ing = RecipeParse.parseIngredient(entry.getValue(), "key '" + keyChar + "'");
                keys.put(keyChar, ing);
            }

            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, recipeId, output.count() + "x " + output.itemId(),
                    "Shaped recipe with pattern: " + String.join(" / ", pattern));
            service.addShaped(recipeId, output, pattern, keys);
            return null;
        });

        // recipes:addShapeless("instant_bread", item("minecraft:bread", 4), {"minecraft:wheat", "minecraft:sugar"})
        table.rawset("addShapeless", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 3)) {
                throw new IllegalArgumentException("Missing arguments for addShapeless");
            }
            String id = args[off].asString();
            ItemCount output = RecipeParse.parseItemCount(args[off + 1], RecipeBindingSupport.ARG_OUTPUT);

            List<IngredientWrapper> ingredients = new ArrayList<>();
            ILuaTable ingTable = args[off + 2].asTable();
            int ingLen = ingTable.length();
            for (int i = 1; i <= ingLen; i++) {
                ingredients.add(RecipeParse.parseIngredient(ingTable.rawget(i), "ingredient #" + i));
            }

            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(),
                    "Shapeless recipe (" + ingredients.size() + " inputs)");
            service.addShapeless(id, output, ingredients);
            return null;
        });
    }

    static void bindCookingRecipes(ILuaTable table, IRecipeManagerService service) {
        bindCookingRecipe(table, "addSmelting", "Smelting", service::addSmelting);
        bindCookingRecipe(table, "addBlasting", "Blasting", service::addBlasting);
        bindCookingRecipe(table, "addSmoking", "Smoking", service::addSmoking);
        bindCookingRecipe(table, "addCampfire", "Campfire", service::addCampfire);
    }

    @FunctionalInterface
    private interface CookingRegistrar {
        void register(String id, ItemCount output, IngredientWrapper input, float xp, int cookTime);
    }

    private static void bindCookingRecipe(ILuaTable table, String name,
            String label, CookingRegistrar registrar) {
        // recipes:addSmelting("ruby_smelt", item("luatweaker:custom_ruby"), "luatweaker:ruby_ore", 1.5, 200)
        table.rawset(name, args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 5)) {
                throw new IllegalArgumentException("Missing arguments for " + name);
            }
            String id = args[off].asString();
            ItemCount output = RecipeParse.parseItemCount(args[off + 1], RecipeBindingSupport.ARG_OUTPUT);
            IngredientWrapper inIng = RecipeParse.parseIngredient(args[off + 2], RecipeBindingSupport.ARG_INPUT);
            float xp = (float) args[off + 3].asDouble();
            int cookTime = args[off + 4].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(),
                    label + " recipe (xp: " + xp + TIME_DETAIL_SEPARATOR + cookTime + ")");
            registrar.register(id, output, inIng, xp, cookTime);
            return null;
        });
    }

    static void bindStoneSmithingRecipes(ILuaTable table, IRecipeManagerService service) {
        // recipes:addStonecutting("cut_copper", item("minecraft:cut_copper"), "minecraft:copper_block")
        table.rawset("addStonecutting", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 3)) {
                throw new IllegalArgumentException("Missing arguments for addStonecutting");
            }
            String id = args[off].asString();
            ItemCount output = RecipeParse.parseItemCount(args[off + 1], RecipeBindingSupport.ARG_OUTPUT);
            IngredientWrapper input = RecipeParse.parseIngredient(args[off + 2], RecipeBindingSupport.ARG_INPUT);
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(),
                    "Stonecutting recipe");
            service.addStonecutting(id, output, input);
            return null;
        });

        // recipes:addSmithing("ruby_upgrade", output, template, base, addition)
        table.rawset("addSmithing", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 5)) {
                throw new IllegalArgumentException("Missing arguments for addSmithing");
            }
            String id = args[off].asString();
            ItemCount output = RecipeParse.parseItemCount(args[off + 1], RecipeBindingSupport.ARG_OUTPUT);
            IngredientWrapper tmpl = RecipeParse.parseIngredient(args[off + 2], "template");
            IngredientWrapper base = RecipeParse.parseIngredient(args[off + 3], "base");
            IngredientWrapper add = RecipeParse.parseIngredient(args[off + 4], "addition");
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(),
                    "Smithing recipe");
            service.addSmithing(id, output, tmpl, base, add);
            return null;
        });
    }

    static void bindAnvilBrewingTradeRecipes(ILuaTable table, IRecipeManagerService service) {
        // recipes:addAnvil("ruby_empower_sword", item("minecraft:diamond_sword"), leftInput, rightInput, expCost)
        table.rawset("addAnvil", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 5)) {
                throw new IllegalArgumentException("Missing arguments for addAnvil");
            }
            String id = args[off].asString();
            ItemCount output = RecipeParse.parseItemCount(args[off + 1], RecipeBindingSupport.ARG_OUTPUT);
            IngredientWrapper left = RecipeParse.parseIngredient(args[off + 2], "leftInput");
            IngredientWrapper right = RecipeParse.parseIngredient(args[off + 3], "rightInput");
            int expCost = args[off + 4].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(),
                    "Anvil recipe (expCost: " + expCost + ")");
            service.addAnvil(id, output, left, right, expCost);
            return null;
        });

        // recipes:addBrewing("ruby_health_potion", "minecraft:strong_healing", "minecraft:healing", "luatweaker:custom_ruby")
        table.rawset("addBrewing", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 4)) {
                throw new IllegalArgumentException("Missing arguments for addBrewing");
            }
            String id = args[off].asString();
            String outPotion = args[off + 1].asString();
            String inPotion = args[off + 2].asString();
            IngredientWrapper ing = RecipeParse.parseIngredient(args[off + 3], "ingredient");
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, outPotion, "Brewing recipe from " + inPotion);
            service.addBrewing(id, outPotion, inPotion, ing);
            return null;
        });

        // recipes:addTrade("cleric", 3, item("minecraft:emerald", 5), nil, item("luatweaker:custom_ruby", 1), 12, 10)
        table.rawset("addTrade", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 7)) {
                throw new IllegalArgumentException("Missing arguments for addTrade");
            }
            String prof = args[off].asString();
            int level = args[off + 1].asInt();
            ItemCount buy1 = RecipeParse.parseItemCount(args[off + 2], "buy1");
            ItemCount buy2 = null;
            if (!args[off + 3].isNil()) {
                buy2 = RecipeParse.parseItemCount(args[off + 3], "buy2");
            }
            ItemCount sell = RecipeParse.parseItemCount(args[off + 4], "sell");
            int maxUses = args[off + 5].asInt();
            int xp = args[off + 6].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, prof + "_lvl" + level,
                    sell.count() + "x " + sell.itemId(), "Villager trade recipe");
            service.addTrade(prof, level, buy1, buy2, sell, maxUses, xp);
            return null;
        });
    }
}
