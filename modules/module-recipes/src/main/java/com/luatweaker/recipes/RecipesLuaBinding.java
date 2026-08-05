package com.luatweaker.recipes;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.*;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipesLuaBinding {

    private static ItemCount parseItemCount(ILuaValue val, String argName) {
        Object obj = val.toJavaObject();
        if (obj instanceof ItemCount ic) {
            return ic;
        }
        if (obj instanceof String s) {
            return new ItemCount(s, 1);
        }
        if (obj instanceof IItem item) {
            return new ItemCount(item.getId(), item.getCount());
        }
        if (val.isTable()) {
            ILuaTable tbl = val.asTable();
            ILuaValue idVal = tbl.rawget("id");
            if (idVal.isNil()) idVal = tbl.rawget("item");
            Object idObj = idVal.toJavaObject();
            if (idObj instanceof String idStr) {
                ILuaValue countVal = tbl.rawget("count");
                int count = (countVal.toJavaObject() instanceof Number n) ? n.intValue() : 1;

                ILuaValue dmgVal = tbl.rawget("damage");
                if (dmgVal.isNil()) dmgVal = tbl.rawget("durability");
                int damage = (dmgVal.toJavaObject() instanceof Number n) ? n.intValue() : 0;

                ILuaValue nameVal = tbl.rawget("name");
                if (nameVal.isNil()) nameVal = tbl.rawget("displayName");
                String name = (nameVal.toJavaObject() instanceof String s) ? s : null;

                List<String> loreList = new ArrayList<>();
                ILuaValue loreVal = tbl.rawget("lore");
                if (loreVal.isTable()) {
                    ILuaTable loreTbl = loreVal.asTable();
                    int len = loreTbl.length();
                    for (int i = 1; i <= len; i++) {
                        Object lObj = loreTbl.rawget(i).toJavaObject();
                        if (lObj instanceof String s) loreList.add(s);
                    }
                }

                Map<String, Integer> enchs = new HashMap<>();
                ILuaValue enchVal = tbl.rawget("enchantments");
                if (enchVal.isNil()) enchVal = tbl.rawget("enchants");
                if (enchVal.isTable()) {
                    ILuaTable enchTbl = enchVal.asTable();
                    for (Map.Entry<ILuaValue, ILuaValue> entry : enchTbl.asMap().entrySet()) {
                        String enchKey = entry.getKey().asString();
                        int lvl = (entry.getValue().toJavaObject() instanceof Number n) ? n.intValue() : 1;
                        enchs.put(enchKey, lvl);
                    }
                }

                ILuaValue nbtVal = tbl.rawget("nbt");
                if (nbtVal.isNil()) nbtVal = tbl.rawget("components");
                String nbtStr = null;
                if (!nbtVal.isNil()) {
                    nbtStr = nbtVal.toJavaObject().toString();
                }

                return new ItemCount(idStr, count, damage, name, loreList, enchs, nbtStr);
            }
        }
        // Fallback: try asString (may work for string Lua values)
        try {
            String s = val.asString();
            if (s != null && !s.isEmpty()) return new ItemCount(s, 1);
        } catch (Exception e) { com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Ignored exception: " + e.getMessage()); }
        throw new IllegalArgumentException("Expected item ID string or item() wrapper for argument '" + argName + "'");
    }

    private static IngredientWrapper parseIngredient(ILuaValue val, String argName) {
        Object obj = val.toJavaObject();
        if (obj instanceof String s) {
            return new IngredientWrapper(s);
        }
        if (obj instanceof IngredientWrapper ing) {
            return ing;
        }
        if (obj instanceof ItemCount ic) {
            return new IngredientWrapper(ic.itemId());
        }
        if (obj instanceof IItem item) {
            return new IngredientWrapper(item.getId());
        }
        // Fallback: try asString
        try {
            String s = val.asString();
            if (s != null && !s.isEmpty()) return new IngredientWrapper(s);
        } catch (Exception e) { com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Ignored exception: " + e.getMessage()); }
        throw new IllegalArgumentException("Expected ingredient string, tag (#), or ingredient() wrapper for argument '" + argName + "'");
    }

    public static void bind(ILuaTable table, IRecipeManagerService service) {
        bind(null, table, service);
    }

    public static void bind(ILuaEngine engine, ILuaTable table, IRecipeManagerService service) {
        // recipes:removeByOutput("minecraft:diamond_sword")
        table.rawset("removeByOutput", args -> {
            if (args.length < 2) throw new IllegalArgumentException("Missing argument 'output'");
            String output = args[1].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, output, "Removing recipes by output");
            service.removeByOutput(output);
            return null;
        });

        // recipes:removeByInput("minecraft:netherite_scrap")
        table.rawset("removeByInput", args -> {
            if (args.length < 2) throw new IllegalArgumentException("Missing argument 'input'");
            String input = args[1].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null, "Removing recipes containing input: " + input);
            service.removeByInput(input);
            return null;
        });

        // recipes:removeById("minecraft:cake")
        table.rawset("removeById", args -> {
            if (args.length < 2) throw new IllegalArgumentException("Missing argument 'id'");
            String id = args[1].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, id, null, "Removing recipe by ID: " + id);
            service.removeById(id);
            return null;
        });

        // recipes:removeByMod("some_mod")
        table.rawset("removeByMod", args -> {
            if (args.length < 2) throw new IllegalArgumentException("Missing argument 'modId'");
            String modId = args[1].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null, "Removing all recipes from mod: " + modId);
            service.removeByMod(modId);
            return null;
        });

        // recipes:removeByTag("#minecraft:logs")
        table.rawset("removeByTag", args -> {
            if (args.length < 2) throw new IllegalArgumentException("Missing argument 'tag'");
            String tag = args[1].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null, "Removing recipes using tag: " + tag);
            service.removeByTag(tag);
            return null;
        });

        // recipes:removeAll()
        table.rawset("removeAll", args -> {
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REMOVE, null, null, "Clearing ALL recipes!");
            service.removeAll();
            return null;
        });

        // recipes:addShaped("ruby_sword", item("minecraft:diamond_sword"), {" R ", " R ", " S "}, { R = ingredient("luatweaker:custom_ruby"), S = ingredient("minecraft:stick") })
        table.rawset("addShaped", args -> {
            if (args.length < 5) throw new IllegalArgumentException("Missing arguments for addShaped");
            String recipeId = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");

            List<String> pattern = new ArrayList<>();
            ILuaTable patTable = args[3].asTable();
            int patLen = patTable.length();
            for (int i = 1; i <= patLen; i++) {
                pattern.add(patTable.rawget(i).asString());
            }

            Map<String, IngredientWrapper> keys = new HashMap<>();
            ILuaTable keysTable = args[4].asTable();
            for (Map.Entry<ILuaValue, ILuaValue> entry : keysTable.asMap().entrySet()) {
                String keyChar = entry.getKey().asString();
                IngredientWrapper ing = parseIngredient(entry.getValue(), "key '" + keyChar + "'");
                keys.put(keyChar, ing);
            }

            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, recipeId, output.count() + "x " + output.itemId(), "Shaped recipe with pattern: " + String.join(" / ", pattern));
            service.addShaped(recipeId, output, pattern, keys);
            return null;
        });

        // recipes:replaceInput("minecraft:stick", "luatweaker:iron_rod")
        table.rawset("replaceInput", args -> {
            if (args.length < 3) throw new IllegalArgumentException("Missing arguments for replaceInput");
            String target = args[1].asString();
            String replacement = args[2].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REPLACE, null, null, "Replacing input: " + target + " -> " + replacement);
            service.replaceInput(target, replacement);
            return null;
        });

        // recipes:replaceOutput("minecraft:dirt", "minecraft:diamond")
        table.rawset("replaceOutput", args -> {
            if (args.length < 3) throw new IllegalArgumentException("Missing arguments for replaceOutput");
            String target = args[1].asString();
            String replacement = args[2].asString();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_REPLACE, null, replacement, "Replacing output: " + target + " -> " + replacement);
            service.replaceOutput(target, replacement);
            return null;
        });

        // recipes:addSmelting("ruby_smelt", item("luatweaker:custom_ruby"), "luatweaker:ruby_ore", 1.5, 200)
        table.rawset("addSmelting", args -> {
            if (args.length < 6) throw new IllegalArgumentException("Missing arguments for addSmelting");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");
            IngredientWrapper inIng = parseIngredient(args[3], "input");
            float xp = (float) args[4].asDouble();
            int cookTime = args[5].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Smelting recipe (xp: " + xp + ", time: " + cookTime + ")");
            service.addSmelting(id, output, inIng, xp, cookTime);
            return null;
        });

        // recipes:addBlasting("ruby_blast", item("luatweaker:custom_ruby"), "luatweaker:ruby_ore", 2.0, 100)
        table.rawset("addBlasting", args -> {
            if (args.length < 6) throw new IllegalArgumentException("Missing arguments for addBlasting");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");
            IngredientWrapper inIng = parseIngredient(args[3], "input");
            float xp = (float) args[4].asDouble();
            int cookTime = args[5].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Blasting recipe (xp: " + xp + ", time: " + cookTime + ")");
            service.addBlasting(id, output, inIng, xp, cookTime);
            return null;
        });

        // recipes:addSmoking("ruby_smoke", item("luatweaker:custom_ruby"), "luatweaker:ruby_ore", 0.35, 100)
        table.rawset("addSmoking", args -> {
            if (args.length < 6) throw new IllegalArgumentException("Missing arguments for addSmoking");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");
            IngredientWrapper inIng = parseIngredient(args[3], "input");
            float xp = (float) args[4].asDouble();
            int cookTime = args[5].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Smoking recipe (xp: " + xp + ", time: " + cookTime + ")");
            service.addSmoking(id, output, inIng, xp, cookTime);
            return null;
        });

        // recipes:addCampfire("ruby_campfire", item("luatweaker:custom_ruby"), "luatweaker:ruby_ore", 0.35, 600)
        table.rawset("addCampfire", args -> {
            if (args.length < 6) throw new IllegalArgumentException("Missing arguments for addCampfire");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");
            IngredientWrapper inIng = parseIngredient(args[3], "input");
            float xp = (float) args[4].asDouble();
            int cookTime = args[5].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Campfire recipe (xp: " + xp + ", time: " + cookTime + ")");
            service.addCampfire(id, output, inIng, xp, cookTime);
            return null;
        });

        // recipes:addShapeless("instant_bread", item("minecraft:bread", 4), {"minecraft:wheat", "minecraft:sugar"})
        table.rawset("addShapeless", args -> {
            if (args.length < 4) throw new IllegalArgumentException("Missing arguments for addShapeless");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");

            List<IngredientWrapper> ingredients = new ArrayList<>();
            ILuaTable ingTable = args[3].asTable();
            int ingLen = ingTable.length();
            for (int i = 1; i <= ingLen; i++) {
                ingredients.add(parseIngredient(ingTable.rawget(i), "ingredient #" + i));
            }

            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Shapeless recipe (" + ingredients.size() + " inputs)");
            service.addShapeless(id, output, ingredients);
            return null;
        });

        // recipes:addStonecutting("cut_copper", item("minecraft:cut_copper"), "minecraft:copper_block")
        table.rawset("addStonecutting", args -> {
            if (args.length < 4) throw new IllegalArgumentException("Missing arguments for addStonecutting");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");
            IngredientWrapper input = parseIngredient(args[3], "input");
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Stonecutting recipe");
            service.addStonecutting(id, output, input);
            return null;
        });

        // recipes:addSmithing("ruby_upgrade", output, template, base, addition)
        table.rawset("addSmithing", args -> {
            if (args.length < 6) throw new IllegalArgumentException("Missing arguments for addSmithing");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");
            IngredientWrapper tmpl = parseIngredient(args[3], "template");
            IngredientWrapper base = parseIngredient(args[4], "base");
            IngredientWrapper add = parseIngredient(args[5], "addition");
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Smithing recipe");
            service.addSmithing(id, output, tmpl, base, add);
            return null;
        });

        // recipes:addAnvil("ruby_empower_sword", item("minecraft:diamond_sword"), leftInput, rightInput, expCost)
        table.rawset("addAnvil", args -> {
            if (args.length < 6) throw new IllegalArgumentException("Missing arguments for addAnvil");
            String id = args[1].asString();
            ItemCount output = parseItemCount(args[2], "output");
            IngredientWrapper left = parseIngredient(args[3], "leftInput");
            IngredientWrapper right = parseIngredient(args[4], "rightInput");
            int expCost = args[5].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, output.count() + "x " + output.itemId(), "Anvil recipe (expCost: " + expCost + ")");
            service.addAnvil(id, output, left, right, expCost);
            return null;
        });

        // recipes:addBrewing("ruby_health_potion", "minecraft:strong_healing", "minecraft:healing", "luatweaker:custom_ruby")
        table.rawset("addBrewing", args -> {
            if (args.length < 5) throw new IllegalArgumentException("Missing arguments for addBrewing");
            String id = args[1].asString();
            String outPotion = args[2].asString();
            String inPotion = args[3].asString();
            IngredientWrapper ing = parseIngredient(args[4], "ingredient");
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, id, outPotion, "Brewing recipe from " + inPotion);
            service.addBrewing(id, outPotion, inPotion, ing);
            return null;
        });

        // recipes:addTrade("cleric", 3, item("minecraft:emerald", 5), nil, item("luatweaker:custom_ruby", 1), 12, 10)
        table.rawset("addTrade", args -> {
            if (args.length < 8) throw new IllegalArgumentException("Missing arguments for addTrade");
            String prof = args[1].asString();
            int level = args[2].asInt();
            ItemCount buy1 = parseItemCount(args[3], "buy1");
            ItemCount buy2 = null;
            if (!args[4].isNil()) {
                buy2 = parseItemCount(args[4], "buy2");
            }
            ItemCount sell = parseItemCount(args[5], "sell");
            int maxUses = args[6].asInt();
            int xp = args[7].asInt();
            LuaTweakerLog.get().recipe(LogStage.RECIPE_ADD, prof + "_lvl" + level, sell.count() + "x " + sell.itemId(), "Villager trade recipe");
            service.addTrade(prof, level, buy1, buy2, sell, maxUses, xp);
            return null;
        });

        // -------------------------------------------------------------
        // Recipe Builder API: Recipe.Shaped("id"):Pattern(...):Key(...):Output(...):Register()
        // -------------------------------------------------------------
        if (engine != null) {
            ILuaTable recipeTable = engine.createTable();
            recipeTable.rawset("Shaped", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                String id = args[off].asString();
                ILuaTable builder = engine.createTable();
                final List<String> pattern = new ArrayList<>();
                final Map<String, IngredientWrapper> keys = new HashMap<>();
                final ItemCount[] output = new ItemCount[1];

                builder.rawset("Pattern", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1 && a[aOff].isTable()) {
                        ILuaTable patTbl = a[aOff].asTable();
                        for (int i = 1; ; i++) {
                            ILuaValue line = patTbl.rawget(i);
                            if (line == null || line.isNil()) break;
                            pattern.add(line.asString());
                        }
                    }
                    return builder;
                });

                builder.rawset("Key", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 2) {
                        String keyChar = a[aOff].asString();
                        IngredientWrapper ing = parseIngredient(a[aOff + 1], "key");
                        keys.put(keyChar, ing);
                    }
                    return builder;
                });

                builder.rawset("Output", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1) {
                        ItemCount out = parseItemCount(a[aOff], "output");
                        if (a.length - aOff >= 2 && !a[aOff + 1].isNil()) {
                            out = new ItemCount(out.itemId(), a[aOff + 1].asInt());
                        }
                        output[0] = out;
                    }
                    return builder;
                });

                builder.rawset("Register", a -> {
                    if (output[0] != null && !pattern.isEmpty()) {
                        service.addShaped(id, output[0], pattern, keys);
                    }
                    return builder;
                });

                return builder;
            });

            recipeTable.rawset("Shapeless", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                String id = args[off].asString();
                ILuaTable builder = engine.createTable();
                final List<IngredientWrapper> inputs = new ArrayList<>();
                final ItemCount[] output = new ItemCount[1];

                builder.rawset("Inputs", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1 && a[aOff].isTable()) {
                        ILuaTable inTbl = a[aOff].asTable();
                        for (int i = 1; ; i++) {
                            ILuaValue entry = inTbl.rawget(i);
                            if (entry == null || entry.isNil()) break;
                            inputs.add(parseIngredient(entry, "input"));
                        }
                    }
                    return builder;
                });

                builder.rawset("Output", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1) {
                        ItemCount out = parseItemCount(a[aOff], "output");
                        if (a.length - aOff >= 2 && !a[aOff + 1].isNil()) {
                            out = new ItemCount(out.itemId(), a[aOff + 1].asInt());
                        }
                        output[0] = out;
                    }
                    return builder;
                });

                builder.rawset("Register", a -> {
                    if (output[0] != null) {
                        service.addShapeless(id, output[0], inputs);
                    }
                    return builder;
                });

                return builder;
            });

            recipeTable.rawset("Smelting", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                String id = args[off].asString();
                ILuaTable builder = engine.createTable();
                final IngredientWrapper[] input = new IngredientWrapper[1];
                final ItemCount[] output = new ItemCount[1];
                final float[] xp = new float[]{ 1.0f };
                final int[] time = new int[]{ 200 };

                builder.rawset("Input", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) input[0] = parseIngredient(a[aOff], "input"); return builder; });
                builder.rawset("Output", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1) {
                        ItemCount out = parseItemCount(a[aOff], "output");
                        if (a.length - aOff >= 2 && !a[aOff + 1].isNil()) out = new ItemCount(out.itemId(), a[aOff + 1].asInt());
                        output[0] = out;
                    }
                    return builder;
                });
                builder.rawset("Xp", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) xp[0] = (float) a[aOff].asDouble(); return builder; });
                builder.rawset("CookingTime", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) time[0] = a[aOff].asInt(); return builder; });
                builder.rawset("Register", a -> {
                    if (output[0] != null && input[0] != null) service.addSmelting(id, output[0], input[0], xp[0], time[0]);
                    return builder;
                });
                return builder;
            });

            recipeTable.rawset("Blasting", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                String id = args[off].asString();
                ILuaTable builder = engine.createTable();
                final IngredientWrapper[] input = new IngredientWrapper[1];
                final ItemCount[] output = new ItemCount[1];
                final float[] xp = new float[]{ 2.0f };
                final int[] time = new int[]{ 100 };

                builder.rawset("Input", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) input[0] = parseIngredient(a[aOff], "input"); return builder; });
                builder.rawset("Output", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1) {
                        ItemCount out = parseItemCount(a[aOff], "output");
                        if (a.length - aOff >= 2 && !a[aOff + 1].isNil()) out = new ItemCount(out.itemId(), a[aOff + 1].asInt());
                        output[0] = out;
                    }
                    return builder;
                });
                builder.rawset("Xp", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) xp[0] = (float) a[aOff].asDouble(); return builder; });
                builder.rawset("CookingTime", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) time[0] = a[aOff].asInt(); return builder; });
                builder.rawset("Register", a -> {
                    if (output[0] != null && input[0] != null) service.addBlasting(id, output[0], input[0], xp[0], time[0]);
                    return builder;
                });
                return builder;
            });

            recipeTable.rawset("Smoking", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                String id = args[off].asString();
                ILuaTable builder = engine.createTable();
                final IngredientWrapper[] input = new IngredientWrapper[1];
                final ItemCount[] output = new ItemCount[1];
                final float[] xp = new float[]{ 0.35f };
                final int[] time = new int[]{ 100 };

                builder.rawset("Input", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) input[0] = parseIngredient(a[aOff], "input"); return builder; });
                builder.rawset("Output", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1) {
                        ItemCount out = parseItemCount(a[aOff], "output");
                        if (a.length - aOff >= 2 && !a[aOff + 1].isNil()) out = new ItemCount(out.itemId(), a[aOff + 1].asInt());
                        output[0] = out;
                    }
                    return builder;
                });
                builder.rawset("Xp", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) xp[0] = (float) a[aOff].asDouble(); return builder; });
                builder.rawset("CookingTime", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) time[0] = a[aOff].asInt(); return builder; });
                builder.rawset("Register", a -> {
                    if (output[0] != null && input[0] != null) service.addSmoking(id, output[0], input[0], xp[0], time[0]);
                    return builder;
                });
                return builder;
            });

            recipeTable.rawset("Campfire", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                String id = args[off].asString();
                ILuaTable builder = engine.createTable();
                final IngredientWrapper[] input = new IngredientWrapper[1];
                final ItemCount[] output = new ItemCount[1];
                final float[] xp = new float[]{ 0.35f };
                final int[] time = new int[]{ 600 };

                builder.rawset("Input", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) input[0] = parseIngredient(a[aOff], "input"); return builder; });
                builder.rawset("Output", a -> {
                    int aOff = getOffset(a);
                    if (a.length - aOff >= 1) {
                        ItemCount out = parseItemCount(a[aOff], "output");
                        if (a.length - aOff >= 2 && !a[aOff + 1].isNil()) out = new ItemCount(out.itemId(), a[aOff + 1].asInt());
                        output[0] = out;
                    }
                    return builder;
                });
                builder.rawset("Xp", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) xp[0] = (float) a[aOff].asDouble(); return builder; });
                builder.rawset("CookingTime", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) time[0] = a[aOff].asInt(); return builder; });
                builder.rawset("Register", a -> {
                    if (output[0] != null && input[0] != null) service.addCampfire(id, output[0], input[0], xp[0], time[0]);
                    return builder;
                });
                return builder;
            });

            recipeTable.rawset("Smithing", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                String id = args[off].asString();
                ILuaTable builder = engine.createTable();
                final IngredientWrapper[] tmpl = new IngredientWrapper[1];
                final IngredientWrapper[] base = new IngredientWrapper[1];
                final IngredientWrapper[] add = new IngredientWrapper[1];
                final ItemCount[] output = new ItemCount[1];

                builder.rawset("Template", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) tmpl[0] = parseIngredient(a[aOff], "template"); return builder; });
                builder.rawset("Base", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) base[0] = parseIngredient(a[aOff], "base"); return builder; });
                builder.rawset("Addition", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) add[0] = parseIngredient(a[aOff], "addition"); return builder; });
                builder.rawset("Output", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) output[0] = parseItemCount(a[aOff], "output"); return builder; });
                builder.rawset("Register", a -> {
                    if (output[0] != null && tmpl[0] != null && base[0] != null && add[0] != null) {
                        service.addSmithing(id, output[0], tmpl[0], base[0], add[0]);
                    }
                    return builder;
                });
                return builder;
            });

            recipeTable.rawset("ReplaceInput", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off >= 2) service.replaceInput(args[off].asString(), args[off + 1].asString());
                return null;
            });
            recipeTable.rawset("ReplaceOutput", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off >= 2) service.replaceOutput(args[off].asString(), args[off + 1].asString());
                return null;
            });
            recipeTable.rawset("RemoveByOutput", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off >= 1) service.removeByOutput(args[off].asString());
                return null;
            });
            recipeTable.rawset("RemoveById", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off >= 1) service.removeById(args[off].asString());
                return null;
            });
            recipeTable.rawset("RemoveByMod", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off >= 1) service.removeByMod(args[off].asString());
                return null;
            });

            ILuaTable env = engine.getGlobalEnvironment();
            env.rawset("recipes", recipeTable);
            env.rawset("Recipe", recipeTable);

            engine.registerService("Recipes", recipeTable);
            engine.registerService("Recipe", recipeTable);
        }
    }

    private static int getOffset(ILuaValue[] args) {
        if (args != null && args.length > 0 && args[0] != null && args[0].isTable()) {
            return 1;
        }
        return 0;
    }
}
