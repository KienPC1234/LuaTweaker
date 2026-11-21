package com.luatweaker.recipes;

import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds generic data-driven recipe registration (Mekanism, Create, AE2, ...). */
final class RecipeCustomBinding {

    private RecipeCustomBinding() {
    }

    static void bindCustomBindings(ILuaEngine engine, ILuaTable recipeTable, IRecipeManagerService service) {
        bindRegisterCustom(engine, recipeTable, service);
        bindCustom(engine, recipeTable, service);
    }

    private static void bindRegisterCustom(ILuaEngine engine, ILuaTable recipeTable,
            IRecipeManagerService service) {
        recipeTable.rawset("RegisterCustom", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 3)) {
                throw new IllegalArgumentException("Recipe.RegisterCustom requires (recipeType, recipeId, dataTable)");
            }
            String recipeType = args[off].asString();
            String recipeId = args[off + 1].asString();
            ILuaValue dataVal = args[off + 2];
            if (!dataVal.isTable()) {
                throw new IllegalArgumentException("Recipe.RegisterCustom argument 3 must be a table");
            }
            ILuaTable dataTbl = dataVal.asTable();
            if (dataTbl.rawget(RecipeBindingSupport.KEY_TYPE).isNil()) {
                dataTbl.rawset(RecipeBindingSupport.KEY_TYPE, engine.wrapString(recipeType));
            }
            String json = com.luatweaker.core.json.JsonServiceImpl.encodeJson(dataTbl, false);
            service.registerCustom(recipeType, recipeId, json);
            return engine.nilValue();
        });
        recipeTable.rawset("registerCustom", recipeTable.rawget("RegisterCustom"));
    }

    private static void bindCustom(ILuaEngine engine, ILuaTable recipeTable,
            IRecipeManagerService service) {
        recipeTable.rawset("Custom", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("Recipe.Custom requires at least (recipeType, recipeId)");
            }
            String recipeType = args[off].asString();
            String recipeId = args[off + 1].asString();
            final ILuaTable[] storedData = new ILuaTable[1];
            if (RecipeBindingSupport.hasMinArgs(args, off, 3) && args[off + 2].isTable()) {
                storedData[0] = args[off + 2].asTable();
                ensureRecipeType(engine, storedData[0], recipeType);
                String json = com.luatweaker.core.json.JsonServiceImpl.encodeJson(storedData[0], false);
                service.registerCustom(recipeType, recipeId, json);
            }
            ILuaTable builder = engine.createTable();
            builder.rawset("Data", a -> {
                int aOff = LuaBinder.getOffset(a);
                if (RecipeBindingSupport.hasMinArgs(a, aOff, 1) && a[aOff].isTable()) {
                    storedData[0] = a[aOff].asTable();
                    ensureRecipeType(engine, storedData[0], recipeType);
                }
                return builder;
            });
            builder.rawset("data", builder.rawget("Data"));

            builder.rawset(RecipeBindingSupport.METHOD_REGISTER, a -> {
                if (storedData[0] != null) {
                    String json = com.luatweaker.core.json.JsonServiceImpl.encodeJson(storedData[0], false);
                    service.registerCustom(recipeType, recipeId, json);
                }
                return builder;
            });
            builder.rawset("register", builder.rawget(RecipeBindingSupport.METHOD_REGISTER));
            return builder;
        });
        recipeTable.rawset("custom", recipeTable.rawget("Custom"));
    }

    private static void ensureRecipeType(ILuaEngine engine, ILuaTable data, String recipeType) {
        if (data.rawget(RecipeBindingSupport.KEY_TYPE).isNil()) {
            data.rawset(RecipeBindingSupport.KEY_TYPE, engine.wrapString(recipeType));
        }
    }
}
