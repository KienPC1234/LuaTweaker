package com.luatweaker.platform.recipe;

import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NeoForgeRecipeManager implements IRecipeManagerService {
    public enum ModificationType {
        REMOVE_BY_OUTPUT,
        REMOVE_BY_INPUT,
        REMOVE_BY_ID,
        REMOVE_BY_MOD,
        REMOVE_BY_TAG,
        REMOVE_ALL,
        ADD_SHAPELESS,
        ADD_SHAPED,
        REPLACE_INPUT,
        REPLACE_OUTPUT,
        ADD_SMELTING,
        ADD_BLASTING,
        ADD_SMOKING,
        ADD_CAMPFIRE,
        ADD_STONECUTTING,
        ADD_SMITHING,
        ADD_ANVIL,
        ADD_BREWING,
        ADD_TRADE
    }

    public record RecipeModification(ModificationType type, Object data) {}
    public record ShapelessData(String recipeId, ItemCount output, List<IngredientWrapper> ingredients) {}
    public record ShapedData(String recipeId, ItemCount output, List<String> pattern, Map<String, IngredientWrapper> keys) {}
    public record CookingData(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {}
    public record StonecuttingData(String recipeId, ItemCount output, IngredientWrapper input) {}
    public record SmithingData(String recipeId, ItemCount result, IngredientWrapper template, IngredientWrapper base, IngredientWrapper addition) {}
    public record AnvilData(String recipeId, ItemCount output, IngredientWrapper leftInput, IngredientWrapper rightInput, int expCost) {}
    public record BrewingData(String recipeId, String outputPotion, String inputPotion, IngredientWrapper ingredient) {}
    public record TradeData(String profession, int level, ItemCount buy1, ItemCount buy2, ItemCount sell, int maxUses, int xp) {}
    public record ReplacementData(String target, String replacement) {}

    private final List<RecipeModification> modifications = Collections.synchronizedList(new ArrayList<>());

    public List<RecipeModification> getModifications() {
        return Collections.unmodifiableList(modifications);
    }

    @Override
    public void removeByOutput(String output) {
        modifications.add(new RecipeModification(ModificationType.REMOVE_BY_OUTPUT, output));
    }

    @Override
    public void removeByInput(String input) {
        modifications.add(new RecipeModification(ModificationType.REMOVE_BY_INPUT, input));
    }

    @Override
    public void removeById(String id) {
        modifications.add(new RecipeModification(ModificationType.REMOVE_BY_ID, id));
    }

    @Override
    public void removeByMod(String modId) {
        modifications.add(new RecipeModification(ModificationType.REMOVE_BY_MOD, modId));
    }

    @Override
    public void removeByTag(String tag) {
        modifications.add(new RecipeModification(ModificationType.REMOVE_BY_TAG, tag));
    }

    @Override
    public void removeAll() {
        modifications.add(new RecipeModification(ModificationType.REMOVE_ALL, null));
    }

    @Override
    public void addShapeless(String recipeId, ItemCount output, List<IngredientWrapper> ingredients) {
        modifications.add(new RecipeModification(ModificationType.ADD_SHAPELESS, new ShapelessData(recipeId, output, ingredients)));
    }

    @Override
    public void addShaped(String recipeId, ItemCount output, List<String> pattern, Map<String, IngredientWrapper> keys) {
        modifications.add(new RecipeModification(ModificationType.ADD_SHAPED, new ShapedData(recipeId, output, pattern, keys)));
    }

    @Override
    public void replaceInput(String target, String replacement) {
        modifications.add(new RecipeModification(ModificationType.REPLACE_INPUT, new ReplacementData(target, replacement)));
    }

    @Override
    public void replaceOutput(String target, String replacement) {
        modifications.add(new RecipeModification(ModificationType.REPLACE_OUTPUT, new ReplacementData(target, replacement)));
    }

    @Override
    public void addSmelting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
        modifications.add(new RecipeModification(ModificationType.ADD_SMELTING, new CookingData(recipeId, output, input, xp, cookTime)));
    }

    @Override
    public void addBlasting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
        modifications.add(new RecipeModification(ModificationType.ADD_BLASTING, new CookingData(recipeId, output, input, xp, cookTime)));
    }

    @Override
    public void addSmoking(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
        modifications.add(new RecipeModification(ModificationType.ADD_SMOKING, new CookingData(recipeId, output, input, xp, cookTime)));
    }

    @Override
    public void addCampfire(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
        modifications.add(new RecipeModification(ModificationType.ADD_CAMPFIRE, new CookingData(recipeId, output, input, xp, cookTime)));
    }

    @Override
    public void addStonecutting(String recipeId, ItemCount output, IngredientWrapper input) {
        modifications.add(new RecipeModification(ModificationType.ADD_STONECUTTING, new StonecuttingData(recipeId, output, input)));
    }

    @Override
    public void addSmithing(String recipeId, ItemCount output, IngredientWrapper template, IngredientWrapper base, IngredientWrapper addition) {
        modifications.add(new RecipeModification(ModificationType.ADD_SMITHING, new SmithingData(recipeId, output, template, base, addition)));
    }

    @Override
    public void addAnvil(String recipeId, ItemCount output, IngredientWrapper leftInput, IngredientWrapper rightInput, int expCost) {
        modifications.add(new RecipeModification(ModificationType.ADD_ANVIL, new AnvilData(recipeId, output, leftInput, rightInput, expCost)));
    }

    @Override
    public void addBrewing(String recipeId, String outputPotion, String inputPotion, IngredientWrapper ingredient) {
        modifications.add(new RecipeModification(ModificationType.ADD_BREWING, new BrewingData(recipeId, outputPotion, inputPotion, ingredient)));
    }

    @Override
    public void addTrade(String profession, int level, ItemCount buy1, ItemCount buy2, ItemCount sell, int maxUses, int xp) {
        modifications.add(new RecipeModification(ModificationType.ADD_TRADE, new TradeData(profession, level, buy1, buy2, sell, maxUses, xp)));
    }
}
