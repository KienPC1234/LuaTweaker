package com.luatweaker.interception;

import com.luatweaker.api.interception.IInterceptionService;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InterceptionServiceImpl implements IInterceptionService {
    private final Set<String> activeCategories = ConcurrentHashMap.newKeySet();

    @Override
    public void clearPendingInterceptions() {
        activeCategories.clear();
    }

    @Override
    public boolean isInterceptionActive(@NotNull String category) {
        return activeCategories.contains(category);
    }

    @Override
    public void addAnvilRecipe(@NotNull String recipeId, @NotNull String leftInput, @NotNull String rightInput, @NotNull String outputItem, int expCost) {
        registerCategory("anvil");
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.RECIPE_APPLY,
            "[Interception] Registered Anvil Interception: " + recipeId + " (" + leftInput + " + " + rightInput + " -> " + outputItem + ")"
        );
    }

    @Override
    public void addBrewingRecipe(@NotNull String recipeId, @NotNull String inputPotion, @NotNull String catalystItem, @NotNull String outputPotion) {
        registerCategory("brewing");
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.RECIPE_APPLY,
            "[Interception] Registered Brewing Interception: " + recipeId + " (" + inputPotion + " + " + catalystItem + " -> " + outputPotion + ")"
        );
    }

    @Override
    public void addVillagerTrade(@NotNull String profession, int level, @NotNull String buyItem, @NotNull String sellItem, int maxUses, int xp) {
        registerCategory("trade");
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.RECIPE_APPLY,
            "[Interception] Registered Villager Trade Interception: " + profession + " lvl " + level
        );
    }

    public void registerCategory(@NotNull String category) {
        activeCategories.add(category);
    }
}
