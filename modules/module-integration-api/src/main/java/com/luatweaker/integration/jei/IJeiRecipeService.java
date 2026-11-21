package com.luatweaker.integration.jei;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * JEI (Just Enough Items) recipe-view contract, segregated from the generic
 * recipe service ({@code IRecipeManagerService}) so common modules never
 * import integration concepts. Implemented by the platform recipe manager;
 * the recipes binding calls it only when the implementation provides it.
 */
public interface IJeiRecipeService {

    /**
     * Registers a custom JEI recipe category.
     *
     * @param spec category spec map (id, title, icon, background, size, catalysts, slots)
     */
    void addJeiCategory(@NotNull Map<String, Object> spec);

    /**
     * Adds a recipe to a custom JEI recipe category.
     *
     * @param categoryId target category id
     * @param recipe     recipe map (inputs, outputs, xp, cookTime)
     */
    void addJeiRecipe(@NotNull String categoryId, @NotNull Map<String, Object> recipe);
}
