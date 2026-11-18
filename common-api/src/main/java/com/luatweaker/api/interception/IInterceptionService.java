package com.luatweaker.api.interception;

import org.jetbrains.annotations.NotNull;

/**
 * Backward-compatible entry point for anvil, brewing and villager-trade definitions.
 * Every operation is forwarded to the active recipe manager, so it has the same runtime
 * effect as the corresponding {@code Recipe:addAnvil/addBrewing/addTrade} API.
 */
public interface IInterceptionService {
    void addAnvilRecipe(@NotNull String recipeId, @NotNull String leftInput, @NotNull String rightInput,
                        @NotNull String outputItem, int expCost);

    void addBrewingRecipe(@NotNull String recipeId, @NotNull String inputPotion, @NotNull String catalystItem,
                          @NotNull String outputPotion);

    void addVillagerTrade(@NotNull String profession, int level, @NotNull String buyItem, @NotNull String sellItem,
                          int maxUses, int xp);

    /** Number of interception definitions successfully forwarded to the recipe manager. */
    int getRegistrationCount();
}
