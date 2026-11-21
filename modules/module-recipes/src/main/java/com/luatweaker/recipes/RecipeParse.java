package com.luatweaker.recipes;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import org.luava.runtime.LuaException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Parses recipe inputs/outputs from Lua values into engine wrappers. */
final class RecipeParse {

    private RecipeParse() {
    }

    static ItemCount parseItemCount(ILuaValue val, String argName) {
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
            ItemCount parsed = parseItemCountTable(val.asTable());
            if (parsed != null) {
                return parsed;
            }
        }
        // Fallback: try asString (may work for string Lua values)
        String fallback = tryAsString(val);
        if (fallback != null && !fallback.isEmpty()) {
            return new ItemCount(fallback, 1);
        }
        throw new IllegalArgumentException(
                "Expected item ID string or item() wrapper for argument '" + argName + "'");
    }

    private static ItemCount parseItemCountTable(ILuaTable tbl) {
        ILuaValue idVal = tbl.rawget(RecipeBindingSupport.KEY_ID);
        if (idVal.isNil()) {
            idVal = tbl.rawget(RecipeBindingSupport.KEY_ITEM);
        }
        Object idObj = idVal.toJavaObject();
        if (!(idObj instanceof String idStr)) {
            return null;
        }
        int count = readIntOpt(tbl, RecipeBindingSupport.KEY_COUNT, null, 1);
        int damage = readIntOpt(tbl, RecipeBindingSupport.KEY_DAMAGE, RecipeBindingSupport.KEY_DURABILITY, 0);
        String name = readStringOpt(tbl, RecipeBindingSupport.KEY_NAME, RecipeBindingSupport.KEY_DISPLAY_NAME);
        List<String> loreList = readLore(tbl);
        Map<String, Integer> enchs = readEnchantments(tbl);
        String nbtStr = readNbtString(tbl);
        return new ItemCount(idStr, count, damage, name, loreList, enchs, nbtStr);
    }

    private static int readIntOpt(ILuaTable tbl, String key, String alias, int fallback) {
        ILuaValue val = tbl.rawget(key);
        if (val.isNil() && alias != null) {
            val = tbl.rawget(alias);
        }
        return val.toJavaObject() instanceof Number n ? n.intValue() : fallback;
    }

    private static String readStringOpt(ILuaTable tbl, String key, String alias) {
        ILuaValue val = tbl.rawget(key);
        if (val.isNil()) {
            val = tbl.rawget(alias);
        }
        return val.toJavaObject() instanceof String s ? s : null;
    }

    private static List<String> readLore(ILuaTable tbl) {
        List<String> loreList = new ArrayList<>();
        ILuaValue loreVal = tbl.rawget(RecipeBindingSupport.KEY_LORE);
        if (loreVal.isTable()) {
            ILuaTable loreTbl = loreVal.asTable();
            int len = loreTbl.length();
            for (int i = 1; i <= len; i++) {
                Object loreObj = loreTbl.rawget(i).toJavaObject();
                if (loreObj instanceof String line) {
                    loreList.add(line);
                }
            }
        }
        return loreList;
    }

    private static Map<String, Integer> readEnchantments(ILuaTable tbl) {
        Map<String, Integer> enchs = new ConcurrentHashMap<>();
        ILuaValue enchVal = tbl.rawget(RecipeBindingSupport.KEY_ENCHANTMENTS);
        if (enchVal.isNil()) {
            enchVal = tbl.rawget(RecipeBindingSupport.KEY_ENCHANTS);
        }
        if (enchVal.isTable()) {
            ILuaTable enchTbl = enchVal.asTable();
            for (Map.Entry<ILuaValue, ILuaValue> entry : enchTbl.asMap().entrySet()) {
                String enchKey = entry.getKey().asString();
                int lvl = entry.getValue().toJavaObject() instanceof Number n ? n.intValue() : 1;
                enchs.put(enchKey, lvl);
            }
        }
        return enchs;
    }

    private static String readNbtString(ILuaTable tbl) {
        ILuaValue nbtVal = tbl.rawget(RecipeBindingSupport.KEY_NBT);
        if (nbtVal.isNil()) {
            nbtVal = tbl.rawget(RecipeBindingSupport.KEY_COMPONENTS);
        }
        if (!nbtVal.isNil()) {
            return nbtVal.toJavaObject().toString();
        }
        return null;
    }

    static IngredientWrapper parseIngredient(ILuaValue val, String argName) {
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
        String fallback = tryAsString(val);
        if (fallback != null && !fallback.isEmpty()) {
            return new IngredientWrapper(fallback);
        }
        throw new IllegalArgumentException(
                "Expected ingredient string, tag (#), or ingredient() wrapper for argument '" + argName + "'");
    }

    private static String tryAsString(ILuaValue val) {
        try {
            return val.asString();
        } catch (LuaException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Ignored exception: " + e.getMessage());
        }
        return null;
    }
}
