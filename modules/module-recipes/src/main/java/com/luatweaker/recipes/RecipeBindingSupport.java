package com.luatweaker.recipes;

import com.luatweaker.api.vm.ILuaValue;

/** Shared glue for the recipe binding collaborators: arg checks and well-known key names. */
final class RecipeBindingSupport {

    static final String ARG_OUTPUT = "output";
    static final String ARG_INPUT = "input";
    static final String METHOD_OUTPUT = "Output";
    static final String METHOD_REGISTER = "Register";
    static final String METHOD_INPUT = "Input";
    static final String METHOD_COOKING_TIME = "CookingTime";
    static final String KEY_TYPE = "type";
    static final String KEY_ID = "id";
    static final String KEY_ITEM = "item";
    static final String KEY_COUNT = "count";
    static final String KEY_DAMAGE = "damage";
    static final String KEY_DURABILITY = "durability";
    static final String KEY_NAME = "name";
    static final String KEY_DISPLAY_NAME = "displayName";
    static final String KEY_LORE = "lore";
    static final String KEY_ENCHANTMENTS = "enchantments";
    static final String KEY_ENCHANTS = "enchants";
    static final String KEY_NBT = "nbt";
    static final String KEY_COMPONENTS = "components";

    private RecipeBindingSupport() {
    }

    static boolean hasMinArgs(ILuaValue[] args, int off, int min) {
        return args.length - off >= min;
    }

    static void bindFirstString(ILuaValue[] args, java.util.function.Consumer<String> setter) {
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        if (hasMinArgs(args, off, 1)) {
            setter.accept(args[off].asString());
        }
    }

    static void bindFirstIngredientTo(ILuaValue[] args,
            java.util.function.Consumer<com.luatweaker.api.wrapper.IngredientWrapper> setter) {
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        if (hasMinArgs(args, off, 1)) {
            setter.accept(RecipeParse.parseIngredient(args[off], ARG_INPUT));
        }
    }

    static void bindFirstDoubleTo(ILuaValue[] args, java.util.function.DoubleConsumer setter) {
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        if (hasMinArgs(args, off, 1)) {
            setter.accept(args[off].asDouble());
        }
    }

    static void bindFirstIntTo(ILuaValue[] args, java.util.function.IntConsumer setter) {
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        if (hasMinArgs(args, off, 1)) {
            setter.accept(args[off].asInt());
        }
    }
}
