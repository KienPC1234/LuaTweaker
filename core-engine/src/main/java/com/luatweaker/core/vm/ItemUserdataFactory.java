package com.luatweaker.core.vm;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaUserdata;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.ValueFactory;
import org.squiddev.cobalt.Varargs;
import org.squiddev.cobalt.function.VarArgFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Lua userdata wrappers for the recipe DSL item/ingredient helpers
 * ({@code item(...)} and {@code ingredient(...)}). Extracted from
 * {@link CobaltLuaEngine} so the engine stays focused on VM lifecycle.
 */
final class ItemUserdataFactory {

    private ItemUserdataFactory() {}

    static LuaUserdata createItemUserdata(LuaState state, ItemCount ic) {
        LuaTable meta = new LuaTable();

        VarArgFunction withCount = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                int count = args.arg(2).checkInteger();
                return createItemUserdata(state, current.withCount(count));
            }
        };
        VarArgFunction withDamage = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                int dmg = args.arg(2).checkInteger();
                return createItemUserdata(state, current.withDamage(dmg));
            }
        };
        VarArgFunction withName = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String name = args.arg(2).checkLuaString().toString();
                return createItemUserdata(state, current.withName(name));
            }
        };
        VarArgFunction withLore = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                List<String> loreList = new ArrayList<>();
                if (args.arg(2) instanceof LuaTable tbl) {
                    int len = tbl.length();
                    for (int i = 1; i <= len; i++) {
                        loreList.add(tbl.rawget(i).toString());
                    }
                } else {
                    loreList.add(args.arg(2).toString());
                }
                return createItemUserdata(state, current.withLore(loreList));
            }
        };
        VarArgFunction withEnchantment = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String ench = args.arg(2).checkLuaString().toString();
                int level = args.arg(3).isNil() ? 1 : args.arg(3).checkInteger();
                return createItemUserdata(state, current.withEnchantment(ench, level));
            }
        };
        VarArgFunction withNbt = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String nbt = args.arg(2).toString();
                return createItemUserdata(state, current.withNbt(nbt));
            }
        };

        try {
            meta.rawset("withCount", withCount);       meta.rawset("count", withCount);
            meta.rawset("withDamage", withDamage);     meta.rawset("damage", withDamage);
            meta.rawset("withName", withName);         meta.rawset("name", withName);
            meta.rawset("withLore", withLore);         meta.rawset("lore", withLore);
            meta.rawset("withEnchantment", withEnchantment); meta.rawset("enchant", withEnchantment);
            meta.rawset("withNbt", withNbt);           meta.rawset("nbt", withNbt);

            LuaTable indexTable = new LuaTable();
            indexTable.rawset("id", ValueFactory.valueOf(ic.itemId()));
            indexTable.rawset("count", ValueFactory.valueOf(ic.count()));
            indexTable.rawset("damage", ValueFactory.valueOf(ic.damage()));

            meta.rawset(Constants.INDEX, new VarArgFunction() {
                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    LuaValue key = args.arg(2);
                    LuaValue val = meta.rawget(key);
                    if (!val.isNil()) return val;
                    return indexTable.rawget(key);
                }
            });
        } catch (LuaError e) { LuaTweakerLog.get().error(LogStage.RUNTIME_ERROR, "LuaError ignored: " + e.getMessage()); }

        LuaUserdata ud = new LuaUserdata(ic);
        ud.setMetatable(state, meta);
        return ud;
    }

    static LuaUserdata createIngredientUserdata(LuaState state, IngredientWrapper ing) {
        LuaTable meta = new LuaTable();
        VarArgFunction orFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                IngredientWrapper current = (IngredientWrapper) ud.instance;
                LuaValue otherArg = args.arg(2);
                if (otherArg instanceof LuaUserdata otherUd && otherUd.instance instanceof IngredientWrapper otherIng) {
                    return createIngredientUserdata(state, current.or(otherIng));
                }
                return createIngredientUserdata(state, current.or(otherArg.toString()));
            }
        };

        try {
            meta.rawset("orIngredient", orFunc);
            meta.rawset("alt", orFunc);
            meta.rawset("otherwise", orFunc);
            meta.rawset("or", orFunc);
            meta.rawset(Constants.INDEX, meta);
        } catch (LuaError e) { LuaTweakerLog.get().error(LogStage.RUNTIME_ERROR, "LuaError ignored: " + e.getMessage()); }

        LuaUserdata ud = new LuaUserdata(ing);
        ud.setMetatable(state, meta);
        return ud;
    }
}
