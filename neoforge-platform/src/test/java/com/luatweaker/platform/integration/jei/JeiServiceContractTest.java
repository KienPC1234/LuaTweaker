package com.luatweaker.platform.integration.jei;

import com.luatweaker.integration.jei.IJeiRecipeService;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The platform recipe manager must honor the segregated JEI contract, so Lua
 * scripts keep working while common modules stay JEI-free.
 */
class JeiServiceContractTest {

    @BeforeEach
    void setup() {
        CustomJeiRegistry.clear();
    }

    @Test
    void recipeManagerImplementsJeiContract() {
        NeoForgeRecipeManager manager = new NeoForgeRecipeManager();

        assertTrue(manager instanceof IJeiRecipeService,
                "platform manager must implement the integration contract");
    }

    @Test
    void jeiCategoryRoutesToTheRegistry() {
        NeoForgeRecipeManager manager = new NeoForgeRecipeManager();
        manager.addJeiCategory(Map.of("id", "contract_cat", "title", "Contract"));

        assertEquals(1, CustomJeiRegistry.getCategories().size(),
                "a JEI category added through the manager must land in the registry");
    }

    @Test
    void jeiRecipeRoutesToTheRegistry() {
        NeoForgeRecipeManager manager = new NeoForgeRecipeManager();
        manager.addJeiCategory(Map.of("id", "contract_cat", "title", "Contract"));
        manager.addJeiRecipe("contract_cat", Map.of("inputs", List.of("minecraft:iron_ore")));

        assertEquals(1, CustomJeiRegistry.getRecipesForCategory(
                        net.minecraft.resources.ResourceLocation.parse("luatweaker:contract_cat")).size(),
                "a JEI recipe added through the manager must land in the registry");
    }
}
