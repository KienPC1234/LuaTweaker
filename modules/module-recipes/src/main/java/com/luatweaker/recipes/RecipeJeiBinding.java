package com.luatweaker.recipes;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Binds JEI category/recipe integration builders. */
final class RecipeJeiBinding {

    private RecipeJeiBinding() {
    }

    static void bindJeiBindings(ILuaEngine engine, ILuaTable recipeTable, IRecipeManagerService service) {
        bindJeiCategory(engine, recipeTable, service);
        bindJeiRecipe(recipeTable, service);
    }

    private static void bindJeiCategory(ILuaEngine engine, ILuaTable recipeTable, IRecipeManagerService service) {
        recipeTable.rawset("NewJeiCategory", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builder = engine.createTable();
            Map<String, Object> spec = new ConcurrentHashMap<>();
            spec.put("id", id);
            List<String> catalysts = new ArrayList<>();
            List<Map<String, Object>> slots = new ArrayList<>();
            spec.put("catalysts", catalysts);
            spec.put("slots", slots);
            spec.put("width", 150);
            spec.put("height", 60);

            bindJeiCategoryBasics(builder, spec, catalysts);
            bindJeiCategorySlots(builder, spec, slots, service);
            return builder;
        });
    }

    private static void bindJeiCategoryBasics(ILuaTable builder,
            Map<String, Object> spec, List<String> catalysts) {
        builder.rawset("Title", a -> {
            RecipeBindingSupport.bindFirstString(a, v -> spec.put("title", v));
            return builder;
        });
        builder.rawset("Icon", a -> {
            RecipeBindingSupport.bindFirstString(a, v -> spec.put("icon", v));
            return builder;
        });
        builder.rawset("Background", a -> {
            RecipeBindingSupport.bindFirstString(a, v -> spec.put("bg", v));
            return builder;
        });
        builder.rawset("Size", a -> {
            int aOff = LuaBinder.getOffset(a);
            if (RecipeBindingSupport.hasMinArgs(a, aOff, 2)) {
                spec.put("width", a[aOff].asInt());
                spec.put("height", a[aOff + 1].asInt());
            }
            return builder;
        });
        builder.rawset("Catalyst", a -> {
            RecipeBindingSupport.bindFirstString(a, catalysts::add);
            return builder;
        });
    }

    private static void bindJeiCategorySlots(ILuaTable builder,
            Map<String, Object> spec, List<Map<String, Object>> slots, IRecipeManagerService service) {
        builder.rawset("Slot", a -> {
            int aOff = LuaBinder.getOffset(a);
            if (RecipeBindingSupport.hasMinArgs(a, aOff, 2)) {
                Map<String, Object> slotMap = new ConcurrentHashMap<>();
                slotMap.put("x", a[aOff].asInt());
                slotMap.put("y", a[aOff + 1].asInt());
                boolean isInput = !RecipeBindingSupport.hasMinArgs(a, aOff, 3) || a[aOff + 2].asBoolean();
                slotMap.put("isInput", isInput);
                slots.add(slotMap);
            }
            return builder;
        });
        builder.rawset("Arrow", a -> {
            int aOff = LuaBinder.getOffset(a);
            if (RecipeBindingSupport.hasMinArgs(a, aOff, 2)) {
                spec.put("arrowX", a[aOff].asInt());
                spec.put("arrowY", a[aOff + 1].asInt());
            }
            return builder;
        });
        builder.rawset("Flame", a -> {
            int aOff = LuaBinder.getOffset(a);
            if (RecipeBindingSupport.hasMinArgs(a, aOff, 2)) {
                spec.put("flameX", a[aOff].asInt());
                spec.put("flameY", a[aOff + 1].asInt());
            }
            return builder;
        });
        builder.rawset(RecipeBindingSupport.METHOD_REGISTER, a -> {
            if (service instanceof com.luatweaker.integration.jei.IJeiRecipeService jei) {
                jei.addJeiCategory(spec);
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Recipes] NewJeiCategory ignored: recipe service has no JEI integration.");
            }
            return builder;
        });
    }

    private static void bindJeiRecipe(ILuaTable recipeTable, IRecipeManagerService service) {
        recipeTable.rawset("AddJeiRecipe", args -> {
            int off = LuaBinder.getOffset(args);
            if (RecipeBindingSupport.hasMinArgs(args, off, 2)) {
                String catId = args[off].asString();
                ILuaValue recipeVal = args[off + 1];
                if (recipeVal.isTable()) {
                    Map<String, Object> recipeMap = buildJeiRecipeMap(recipeVal.asTable());
                    if (service instanceof com.luatweaker.integration.jei.IJeiRecipeService jei) {
                        jei.addJeiRecipe(catId, recipeMap);
                    } else {
                        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                                "[Recipes] AddJeiRecipe ignored: recipe service has no JEI integration.");
                    }
                }
            }
            return null;
        });
    }

    private static Map<String, Object> buildJeiRecipeMap(ILuaTable tbl) {
        Map<String, Object> recipeMap = new ConcurrentHashMap<>();

        ILuaValue inVal = tbl.rawget("inputs");
        if (inVal.isTable()) {
            recipeMap.put("inputs", readJeiStringList(inVal.asTable()));
        }

        ILuaValue outVal = tbl.rawget("outputs");
        if (outVal.isTable()) {
            recipeMap.put("outputs", readJeiStringList(outVal.asTable()));
        }

        ILuaValue xpVal = tbl.rawget("xp");
        if (!xpVal.isNil() && xpVal.toJavaObject() instanceof Number xpNum) {
            recipeMap.put("xp", xpNum.floatValue());
        }

        ILuaValue timeVal = tbl.rawget("cookTime");
        if (timeVal.isNil()) {
            timeVal = tbl.rawget("time");
        }
        if (!timeVal.isNil() && timeVal.toJavaObject() instanceof Number timeNum) {
            recipeMap.put("cookTime", timeNum.intValue());
        }
        return recipeMap;
    }

    private static List<String> readJeiStringList(ILuaTable listTbl) {
        List<String> list = new ArrayList<>();
        for (int i = 1; i <= listTbl.length(); i++) {
            list.add(listTbl.rawget(i).asString());
        }
        return list;
    }
}


