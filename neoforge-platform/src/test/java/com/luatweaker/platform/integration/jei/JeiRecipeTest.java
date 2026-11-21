package com.luatweaker.platform.integration.jei;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JeiRecipeTest {

    @BeforeEach
    void setup() {
        CustomJeiRegistry.clear();
    }

    private static Map<String, Object> recipeSpec() {
        return Map.of(
                "inputs", List.of("minecraft:iron_ore"),
                "outputs", List.of("minecraft:iron_ingot"),
                "cookTime", 200,
                "xp", 2.5f
        );
    }

    private static List<CustomJeiRegistry.CustomRecipeEntry> registeredRecipes() {
        CustomJeiRegistry.registerRecipeFromSpec("crystal_infusion", recipeSpec());
        return CustomJeiRegistry.getRecipesForCategory(
                net.minecraft.resources.ResourceLocation.parse("luatweaker:crystal_infusion"));
    }

    @Test
    void registeredRecipeCountIsOne() {
        assertEquals(1, registeredRecipes().size(),
                "registering one recipe spec must populate exactly one recipe");
    }

    @Test
    void registeredRecipeKeepsCookTime() {
        assertEquals(200, registeredRecipes().get(0).cookTime(),
                "the cook time must round-trip through the spec");
    }

    @Test
    void registeredRecipeKeepsXp() {
        assertEquals(2.5f, registeredRecipes().get(0).xp(),
                "the experience reward must round-trip through the spec");
    }
}
