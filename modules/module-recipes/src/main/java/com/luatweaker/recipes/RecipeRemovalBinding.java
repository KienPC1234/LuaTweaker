package com.luatweaker.recipes;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;

/** Binds recipe removal and input/output replacement operations. */
final class RecipeRemovalBinding {

    private RecipeRemovalBinding() {
    }

    static void bindRemovals(ILuaTable table, IRecipeManagerService service) {
        table.rawset("removeByOutput", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Missing argument 'output'");
            }
            String output = args[off].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, output, "Removing recipes by output");
            service.removeByOutput(output);
            return null;
        });

        table.rawset("removeByInput", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Missing argument 'input'");
            }
            String input = args[off].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null,
                    "Removing recipes containing input: " + input);
            service.removeByInput(input);
            return null;
        });

        table.rawset("removeById", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Missing argument 'id'");
            }
            String id = args[off].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, id, null, "Removing recipe by ID: " + id);
            service.removeById(id);
            return null;
        });

        table.rawset("removeByMod", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Missing argument 'modId'");
            }
            String modId = args[off].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null,
                    "Removing all recipes from mod: " + modId);
            service.removeByMod(modId);
            return null;
        });

        table.rawset("removeByTag", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Missing argument 'tag'");
            }
            String tag = args[off].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null, "Removing recipes using tag: " + tag);
            service.removeByTag(tag);
            return null;
        });

        table.rawset("removeAll", args -> {
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null, "Clearing ALL recipes!");
            service.removeAll();
            return null;
        });
    }

    static void bindReplacements(ILuaTable table, IRecipeManagerService service) {
        table.rawset("replaceInput", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("Missing arguments for replaceInput");
            }
            String target = args[off].asString();
            String replacement = args[off + 1].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REPLACE, null, null,
                    "Replacing input: " + target + " -> " + replacement);
            service.replaceInput(target, replacement);
            return null;
        });

        table.rawset("replaceOutput", args -> {
            int off = LuaBinder.getOffset(args);
            if (!RecipeBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("Missing arguments for replaceOutput");
            }
            String target = args[off].asString();
            String replacement = args[off + 1].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REPLACE, null, replacement,
                    "Replacing output: " + target + " -> " + replacement);
            service.replaceOutput(target, replacement);
            return null;
        });
    }
    static void bindSimpleRecipeOps(ILuaTable recipeTable, IRecipeManagerService service) {
        recipeTable.rawset("ReplaceInput", args -> {
            int off = LuaBinder.getOffset(args);
            if (RecipeBindingSupport.hasMinArgs(args, off, 2)) {
                service.replaceInput(args[off].asString(), args[off + 1].asString());
            }
            return null;
        });
        recipeTable.rawset("ReplaceOutput", args -> {
            int off = LuaBinder.getOffset(args);
            if (RecipeBindingSupport.hasMinArgs(args, off, 2)) {
                service.replaceOutput(args[off].asString(), args[off + 1].asString());
            }
            return null;
        });
        recipeTable.rawset("RemoveByOutput", args -> {
            int off = LuaBinder.getOffset(args);
            if (RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                service.removeByOutput(args[off].asString());
            }
            return null;
        });
        recipeTable.rawset("RemoveById", args -> {
            int off = LuaBinder.getOffset(args);
            if (RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                service.removeById(args[off].asString());
            }
            return null;
        });
        recipeTable.rawset("RemoveByMod", args -> {
            int off = LuaBinder.getOffset(args);
            if (RecipeBindingSupport.hasMinArgs(args, off, 1)) {
                service.removeByMod(args[off].asString());
            }
            return null;
        });
    }
}
