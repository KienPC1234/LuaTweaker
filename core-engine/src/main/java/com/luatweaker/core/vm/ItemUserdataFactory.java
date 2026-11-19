package com.luatweaker.core.vm;

import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import org.luava.runtime.LuaException;
import org.luava.runtime.LuaFunction;
import org.luava.runtime.LuaNil;
import org.luava.runtime.LuaState;
import org.luava.runtime.LuaTable;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds luava userdata wrappers for the recipe DSL item/ingredient helpers
 * ({@code item(...)} and {@code ingredient(...)}).
 */
public final class ItemUserdataFactory {

    private ItemUserdataFactory() {}

    /**
     * Builds the luava userdata behind the recipe DSL {@code item(...)} helper.
     *
     * @param state active luava state
     * @param ic    item stack definition to expose
     * @return userdata with chainable {@code withCount}/{@code withDamage}/{@code withName}/
     *         {@code withLore}/{@code withEnchantment}/{@code withNbt} methods
     */
    public static LuaUserdata createItemUserdataLuava(LuaState state, ItemCount ic) {
        LuaTable meta = new LuaTable();

        LuaFunction withCount = LuaFunction.of(args ->
                createItemUserdataLuava(state, itemFrom(args).withCount((int) requireArg(args, 1).toLong())));
        LuaFunction withDamage = LuaFunction.of(args ->
                createItemUserdataLuava(state, itemFrom(args).withDamage((int) requireArg(args, 1).toLong())));
        LuaFunction withName = LuaFunction.of(args ->
                createItemUserdataLuava(state, itemFrom(args).withName(requireArg(args, 1).toLuaString())));
        LuaFunction withLore = LuaFunction.of(args -> {
            ItemCount current = itemFrom(args);
            LuaValue loreArg = requireArg(args, 1);
            List<String> loreList = new ArrayList<>();
            if (loreArg instanceof LuaTable table) {
                for (int index = 1; index <= table.rawlen(); index++) {
                    loreList.add(table.rawgetInt(index).toLuaString());
                }
            } else {
                loreList.add(loreArg.toLuaString());
            }
            return createItemUserdataLuava(state, current.withLore(loreList));
        });
        LuaFunction withEnchantment = LuaFunction.of(args -> {
            ItemCount current = itemFrom(args);
            String enchantment = requireArg(args, 1).toLuaString();
            int level = 1;
            if (args.length > 2 && args[2] != null && !args[2].isNil()) level = (int) args[2].toLong();
            return createItemUserdataLuava(state, current.withEnchantment(enchantment, level));
        });
        LuaFunction withNbt = LuaFunction.of(args ->
                createItemUserdataLuava(state, itemFrom(args).withNbt(requireArg(args, 1).toLuaString())));

        setMethod(meta, "withCount", withCount);       setMethod(meta, "count", withCount);
        setMethod(meta, "withDamage", withDamage);     setMethod(meta, "damage", withDamage);
        setMethod(meta, "withName", withName);         setMethod(meta, "name", withName);
        setMethod(meta, "withLore", withLore);         setMethod(meta, "lore", withLore);
        setMethod(meta, "withEnchantment", withEnchantment); setMethod(meta, "enchant", withEnchantment);
        setMethod(meta, "withNbt", withNbt);           setMethod(meta, "nbt", withNbt);

        LuaTable indexTable = new LuaTable();
        indexTable.rawset(LuaValue.valueOf("id"), LuaValue.valueOf(ic.itemId()));
        indexTable.rawset(LuaValue.valueOf("count"), LuaValue.valueOf(ic.count()));
        indexTable.rawset(LuaValue.valueOf("damage"), LuaValue.valueOf(ic.damage()));

        meta.rawset(LuaValue.Meta.INDEX, LuaFunction.of(args -> {
            LuaValue key = args.length > 1 ? args[1] : LuaNil.NIL;
            if (key == null || key.isNil()) return LuaNil.NIL;
            LuaValue method = meta.rawget(key);
            return method.isNil() ? indexTable.rawget(key) : method;
        }));

        LuaUserdata userdata = new LuaUserdata(ic);
        userdata.setMetatable(meta);
        return userdata;
    }

    /**
     * Builds the luava userdata behind the recipe DSL {@code ingredient(...)} helper.
     *
     * @param state active luava state
     * @param ing   ingredient definition to expose
     * @return userdata with chainable {@code orIngredient}/{@code alt}/{@code otherwise}.
     *         Lua names a method {@code or} cannot be used: {@code or} is a reserved keyword,
     *         so {@code ing:or(...)} is a syntax error.
     */
    public static LuaUserdata createIngredientUserdataLuava(LuaState state, IngredientWrapper ing) {
        LuaTable meta = new LuaTable();

        LuaFunction orFunction = LuaFunction.of(args -> {
            IngredientWrapper current = ingredientFrom(args);
            LuaValue other = requireArg(args, 1);
            if (other instanceof LuaUserdata otherUserdata
                    && otherUserdata.getJavaInstance() instanceof IngredientWrapper otherIngredient) {
                return createIngredientUserdataLuava(state, current.or(otherIngredient));
            }
            return createIngredientUserdataLuava(state, current.or(other.toLuaString()));
        });

        setMethod(meta, "orIngredient", orFunction);
        setMethod(meta, "alt", orFunction);
        setMethod(meta, "otherwise", orFunction);
        meta.rawset(LuaValue.valueOf("descriptor"), LuaValue.valueOf(ing.descriptor()));
        meta.rawset(LuaValue.Meta.INDEX, meta);

        LuaUserdata userdata = new LuaUserdata(ing);
        userdata.setMetatable(meta);
        return userdata;
    }

    private static ItemCount itemFrom(LuaValue[] args) {
        if (args.length == 0 || !(args[0] instanceof LuaUserdata userdata)
                || !(userdata.getJavaInstance() instanceof ItemCount itemCount)) {
            throw new LuaException("item userdata expected as first argument");
        }
        return itemCount;
    }

    private static IngredientWrapper ingredientFrom(LuaValue[] args) {
        if (args.length == 0 || !(args[0] instanceof LuaUserdata userdata)
                || !(userdata.getJavaInstance() instanceof IngredientWrapper ingredient)) {
            throw new LuaException("ingredient userdata expected as first argument");
        }
        return ingredient;
    }

    private static LuaValue requireArg(LuaValue[] args, int index) {
        if (args.length <= index || args[index] == null || args[index].isNil()) {
            throw new LuaException("Missing argument #" + (index + 1));
        }
        return args[index];
    }

    private static void setMethod(LuaTable meta, String name, LuaFunction function) {
        meta.rawset(LuaValue.valueOf(name), function);
    }
}
