package com.luatweaker.api.recipe;

import java.util.List;
import java.util.Map;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.api.wrapper.IngredientWrapper;

@LuaDoc(description = "Service for managing and altering Minecraft crafting recipes.")
public interface IRecipeManagerService {
    @LuaDoc(
        description = "Removes all recipes that result in the specified output item.",
        params = {"output: string"},
        returnType = "void"
    )
    void removeByOutput(String output);

    @LuaDoc(
        description = "Removes all recipes that contain the specified input item.",
        params = {"input: string"},
        returnType = "void"
    )
    void removeByInput(String input);

    @LuaDoc(
        description = "Removes a specific recipe by its registry ID.",
        params = {"id: string"},
        returnType = "void"
    )
    void removeById(String id);

    @LuaDoc(
        description = "Removes all recipes registered in the game.",
        returnType = "void"
    )
    void removeAll();

    @LuaDoc(
        description = "Removes all recipes registered by a specific mod ID.",
        params = {"modId: string"},
        returnType = "void"
    )
    void removeByMod(String modId);

    @LuaDoc(
        description = "Adds a new shapeless crafting recipe.",
        params = {"recipeId: string", "output: IItem", "ingredients: IngredientWrapper[]"},
        returnType = "void"
    )
    void addShapeless(String recipeId, ItemCount output, List<IngredientWrapper> ingredients);

    @LuaDoc(
        description = "Adds a new shaped crafting recipe.",
        params = {"recipeId: string", "output: IItem", "pattern: string[]", "keys: table"},
        returnType = "void"
    )
    void addShaped(String recipeId, ItemCount output, List<String> pattern, Map<String, IngredientWrapper> keys);

    @LuaDoc(
        description = "Globally replaces an input ingredient in all recipes.",
        params = {"target: string", "replacement: string"},
        returnType = "void"
    )
    void replaceInput(String target, String replacement);

    @LuaDoc(
        description = "Globally replaces an output item in all recipes.",
        params = {"target: string", "replacement: string"},
        returnType = "void"
    )
    void replaceOutput(String target, String replacement);

    @LuaDoc(description = "Adds a new furnace smelting recipe.", params = {"recipeId: string", "output: IItem", "input: IngredientWrapper", "xp: number", "cookTime: integer"})
    void addSmelting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime);

    @LuaDoc(description = "Adds a new blast furnace recipe.", params = {"recipeId: string", "output: IItem", "input: IngredientWrapper", "xp: number", "cookTime: integer"})
    void addBlasting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime);

    @LuaDoc(description = "Adds a new smoker recipe.", params = {"recipeId: string", "output: IItem", "input: IngredientWrapper", "xp: number", "cookTime: integer"})
    void addSmoking(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime);

    @LuaDoc(description = "Adds a new campfire cooking recipe.", params = {"recipeId: string", "output: IItem", "input: IngredientWrapper", "xp: number", "cookTime: integer"})
    void addCampfire(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime);

    @LuaDoc(description = "Adds a new stonecutter recipe.", params = {"recipeId: string", "output: IItem", "input: IngredientWrapper"})
    void addStonecutting(String recipeId, ItemCount output, IngredientWrapper input);

    @LuaDoc(description = "Adds a new smithing table upgrade recipe.", params = {"recipeId: string", "output: IItem", "template: IngredientWrapper", "base: IngredientWrapper", "addition: IngredientWrapper"})
    void addSmithing(String recipeId, ItemCount output, IngredientWrapper template, IngredientWrapper base, IngredientWrapper addition);

    @LuaDoc(
        description = "Removes all recipes that use items belonging to the specified tag as input or output.",
        params = {"tag: string"},
        returnType = "void"
    )
    void removeByTag(String tag);

    @LuaDoc(description = "Adds a new anvil combination recipe.", params = {"recipeId: string", "output: IItem", "leftInput: IngredientWrapper", "rightInput: IngredientWrapper", "expCost: integer"})
    void addAnvil(String recipeId, ItemCount output, IngredientWrapper leftInput, IngredientWrapper rightInput, int expCost);

    @LuaDoc(description = "Adds a new brewing stand potion recipe.", params = {"recipeId: string", "outputPotion: string", "inputPotion: string", "ingredient: IngredientWrapper"})
    void addBrewing(String recipeId, String outputPotion, String inputPotion, IngredientWrapper ingredient);

    @LuaDoc(description = "Adds a new villager trade recipe.", params = {"profession: string", "level: integer", "buy1: IItem", "buy2: IItem", "sell: IItem", "maxUses: integer", "xp: integer"})
    void addTrade(String profession, int level, ItemCount buy1, ItemCount buy2, ItemCount sell, int maxUses, int xp);
}
