package com.luatweaker.api.interception;

import org.jetbrains.annotations.NotNull;

public interface IInterceptionService {
    void clearPendingInterceptions();
    boolean isInterceptionActive(@NotNull String category);

    default void addAnvilRecipe(@NotNull String recipeId, @NotNull String leftInput, @NotNull String rightInput, @NotNull String outputItem, int expCost) {}
    default void addBrewingRecipe(@NotNull String recipeId, @NotNull String inputPotion, @NotNull String catalystItem, @NotNull String outputPotion) {}
    default void addVillagerTrade(@NotNull String profession, int level, @NotNull String buyItem, @NotNull String sellItem, int maxUses, int xp) {}
}
