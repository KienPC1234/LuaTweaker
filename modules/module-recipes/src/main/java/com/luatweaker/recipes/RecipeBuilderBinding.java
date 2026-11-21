package com.luatweaker.recipes;

import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.bind.LuaBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds the Recipe builder DSL:
 * {@code Recipe.Shaped("id"):Pattern(...):Key(...):Output(...):Register()}.
 */
final class RecipeBuilderBinding {

    private RecipeBuilderBinding() {
    }

    static void bindRecipeBuilders(ILuaEngine engine, ILuaTable table, IRecipeManagerService service) {
        if (engine == null) {
            return;
        }
        ILuaTable recipeTable = engine.createTable();
        bindShapedBuilder(engine, recipeTable, service);
        bindShapelessBuilder(engine, recipeTable, service);
        bindCookingBuilders(engine, recipeTable, service);
        bindSmithingBuilder(engine, recipeTable, service);
        RecipeRemovalBinding.bindSimpleRecipeOps(recipeTable, service);
        RecipeJeiBinding.bindJeiBindings(engine, recipeTable, service);
        RecipeCustomBinding.bindCustomBindings(engine, recipeTable, service);
        wireRecipeGlobals(engine, recipeTable);
    }

    private static void bindShapedBuilder(ILuaEngine engine, ILuaTable recipeTable, IRecipeManagerService service) {
        recipeTable.rawset("Shaped", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builder = engine.createTable();
            final List<String> pattern = new ArrayList<>();
            final Map<String, IngredientWrapper> keys = new ConcurrentHashMap<>();
            final ItemCount[] output = new ItemCount[1];

            builder.rawset("Pattern", a -> {
                int aOff = LuaBinder.getOffset(a);
                if (RecipeBindingSupport.hasMinArgs(a, aOff, 1) && a[aOff].isTable()) {
                    readPatternLines(a[aOff].asTable(), pattern);
                }
                return builder;
            });

            builder.rawset("Key", a -> {
                int aOff = LuaBinder.getOffset(a);
                if (RecipeBindingSupport.hasMinArgs(a, aOff, 2)) {
                    String keyChar = a[aOff].asString();
                    IngredientWrapper ing = RecipeParse.parseIngredient(a[aOff + 1], "key");
                    keys.put(keyChar, ing);
                }
                return builder;
            });

            bindOutputWithCount(builder, output);

            builder.rawset(RecipeBindingSupport.METHOD_REGISTER, a -> {
                if (output[0] != null && !pattern.isEmpty()) {
                    service.addShaped(id, output[0], pattern, keys);
                }
                return builder;
            });

            return builder;
        });
    }

    private static void readPatternLines(ILuaTable patTbl, List<String> pattern) {
        for (int i = 1; ; i++) {
            ILuaValue line = patTbl.rawget(i);
            if (line == null || line.isNil()) {
                break;
            }
            pattern.add(line.asString());
        }
    }

    private static void bindShapelessBuilder(ILuaEngine engine, ILuaTable recipeTable,
            IRecipeManagerService service) {
        recipeTable.rawset("Shapeless", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builder = engine.createTable();
            final List<IngredientWrapper> inputs = new ArrayList<>();
            final ItemCount[] output = new ItemCount[1];

            builder.rawset("Inputs", a -> {
                int aOff = LuaBinder.getOffset(a);
                if (RecipeBindingSupport.hasMinArgs(a, aOff, 1) && a[aOff].isTable()) {
                    readRecipeInputs(a[aOff].asTable(), inputs);
                }
                return builder;
            });

            bindOutputWithCount(builder, output);

            builder.rawset(RecipeBindingSupport.METHOD_REGISTER, a -> {
                if (output[0] != null) {
                    service.addShapeless(id, output[0], inputs);
                }
                return builder;
            });

            return builder;
        });
    }

    private static void readRecipeInputs(ILuaTable inTbl, List<IngredientWrapper> inputs) {
        for (int i = 1; ; i++) {
            ILuaValue entry = inTbl.rawget(i);
            if (entry == null || entry.isNil()) {
                break;
            }
            inputs.add(RecipeParse.parseIngredient(entry, "input"));
        }
    }

    private static void bindCookingBuilders(ILuaEngine engine, ILuaTable recipeTable,
            IRecipeManagerService service) {
        bindCookingBuilder(engine, recipeTable, "Smelting", 1.0f, 200, service::addSmelting);
        bindCookingBuilder(engine, recipeTable, "Blasting", 2.0f, 100, service::addBlasting);
        bindCookingBuilder(engine, recipeTable, "Smoking", 0.35f, 100, service::addSmoking);
        bindCookingBuilder(engine, recipeTable, "Campfire", 0.35f, 600, service::addCampfire);
    }

    @FunctionalInterface
    private interface CookingBuilderRegistrar {
        void register(String id, ItemCount output, IngredientWrapper input, float xp, int time);
    }

    private static void bindCookingBuilder(ILuaEngine engine, ILuaTable recipeTable,
            String name, float defaultXp, int defaultTime,
            CookingBuilderRegistrar registrar) {
        recipeTable.rawset(name, args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builder = engine.createTable();
            final IngredientWrapper[] input = new IngredientWrapper[1];
            final ItemCount[] output = new ItemCount[1];
            final float[] xp = { defaultXp };
            final int[] time = { defaultTime };

            builder.rawset(RecipeBindingSupport.METHOD_INPUT, a -> {
                RecipeBindingSupport.bindFirstIngredientTo(a, v -> input[0] = v);
                return builder;
            });
            bindOutputWithCount(builder, output);
            builder.rawset("Xp", a -> {
                RecipeBindingSupport.bindFirstDoubleTo(a, v -> xp[0] = (float) v);
                return builder;
            });
            builder.rawset(RecipeBindingSupport.METHOD_COOKING_TIME, a -> {
                RecipeBindingSupport.bindFirstIntTo(a, v -> time[0] = v);
                return builder;
            });
            builder.rawset(RecipeBindingSupport.METHOD_REGISTER, a -> {
                if (output[0] != null && input[0] != null) {
                    registrar.register(id, output[0], input[0], xp[0], time[0]);
                }
                return builder;
            });
            return builder;
        });
    }

    private static void bindOutputWithCount(ILuaTable builder, ItemCount... output) {
        builder.rawset(RecipeBindingSupport.METHOD_OUTPUT, a -> {
            int aOff = LuaBinder.getOffset(a);
            if (RecipeBindingSupport.hasMinArgs(a, aOff, 1)) {
                ItemCount out = RecipeParse.parseItemCount(a[aOff], RecipeBindingSupport.ARG_OUTPUT);
                if (RecipeBindingSupport.hasMinArgs(a, aOff, 2) && !a[aOff + 1].isNil()) {
                    out = new ItemCount(out.itemId(), a[aOff + 1].asInt());
                }
                output[0] = out;
            }
            return builder;
        });
    }

    private static void bindSmithingBuilder(ILuaEngine engine, ILuaTable recipeTable,
            IRecipeManagerService service) {
        recipeTable.rawset("Smithing", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builder = engine.createTable();
            final IngredientWrapper[] tmpl = new IngredientWrapper[1];
            final IngredientWrapper[] base = new IngredientWrapper[1];
            final IngredientWrapper[] add = new IngredientWrapper[1];
            final ItemCount[] output = new ItemCount[1];

            builder.rawset("Template", a -> {
                RecipeBindingSupport.bindFirstIngredientTo(a, v -> tmpl[0] = v);
                return builder;
            });
            builder.rawset("Base", a -> {
                RecipeBindingSupport.bindFirstIngredientTo(a, v -> base[0] = v);
                return builder;
            });
            builder.rawset("Addition", a -> {
                RecipeBindingSupport.bindFirstIngredientTo(a, v -> add[0] = v);
                return builder;
            });
            builder.rawset(RecipeBindingSupport.METHOD_OUTPUT, a -> {
                int aOff = LuaBinder.getOffset(a);
                if (RecipeBindingSupport.hasMinArgs(a, aOff, 1)) {
                    output[0] = RecipeParse.parseItemCount(a[aOff], RecipeBindingSupport.ARG_OUTPUT);
                }
                return builder;
            });
            builder.rawset(RecipeBindingSupport.METHOD_REGISTER, a -> {
                if (output[0] != null && tmpl[0] != null && base[0] != null && add[0] != null) {
                    service.addSmithing(id, output[0], tmpl[0], base[0], add[0]);
                }
                return builder;
            });
            return builder;
        });
    }


    private static final String RECIPE_MODULE = "Recipe";

    private static void wireRecipeGlobals(ILuaEngine engine, ILuaTable recipeTable) {
        ILuaTable env = engine.getGlobalEnvironment();
        env.rawset("recipes", recipeTable);
        env.rawset(RECIPE_MODULE, recipeTable);

        engine.registerService("Recipes", recipeTable);
        engine.registerService(RECIPE_MODULE, recipeTable);

        com.luatweaker.core.stub.StubCatalog.register(RECIPE_MODULE, IRecipeManagerService.class);
        com.luatweaker.core.stub.StubCatalog.register("Recipes", IRecipeManagerService.class);
        com.luatweaker.core.vm.NativeModuleResolver.registerModule(RECIPE_MODULE, "Recipes", "recipes");
    }
}


