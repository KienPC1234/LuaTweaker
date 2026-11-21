package com.luatweaker.recipes;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies data-driven custom recipes (RegisterCustom builder and the
 * Custom/Data/Register chain).
 */
class CustomRecipeTest extends RecipeScriptFixture {

    private static final String CUSTOM_CHUNK = "custom_recipe_test";
    private static final String CUSTOM_SCRIPT =
            "Recipe.RegisterCustom('mekanism:crushing', 'mymod:crush_copper', {\n" +
            "    input = { count = 1, item = 'minecraft:copper_ingot' },\n" +
            "    output = { count = 1, item = 'mekanism:dust_copper' }\n" +
            "})\n" +
            "Recipe.Custom('create:mixing', 'mymod:mix_potion')\n" +
            "    :Data({ heat = 'heated', output = 'mymod:tea' })\n" +
            "    :Register()\n";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private void runCustomScript(ILuaEngine engine) {
        ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(engine, recipesTable, recipeService);
        engine.executeString(CUSTOM_SCRIPT, CUSTOM_CHUNK);
    }

    @Test
    void registersTwoCustomRecipes() {
        ILuaEngine engine = newBoundEngine();
        runCustomScript(engine);

        assertEquals(2, recipeHandler.customRecipes.size(),
                "both the RegisterCustom call and the Custom chain must register");
    }

    @Test
    void recordsCrushingRecipe() {
        ILuaEngine engine = newBoundEngine();
        runCustomScript(engine);

        assertTrue(recipeHandler.customRecipes.get(0).contains("mekanism:crushing|mymod:crush_copper|"),
                "the crushing recipe must record type, id and data");
    }

    @Test
    void recordsCrushingType() {
        ILuaEngine engine = newBoundEngine();
        runCustomScript(engine);

        assertTrue(recipeHandler.customRecipes.get(0).contains("\"type\":\"mekanism:crushing\""),
                "the crushing recipe data must carry its type field");
    }

    @Test
    void recordsMixingRecipe() {
        ILuaEngine engine = newBoundEngine();
        runCustomScript(engine);

        assertTrue(recipeHandler.customRecipes.get(1).contains("create:mixing|mymod:mix_potion|"),
                "the mixing recipe must record type, id and data");
    }

    @Test
    void recordsMixingHeat() {
        ILuaEngine engine = newBoundEngine();
        runCustomScript(engine);

        assertTrue(recipeHandler.customRecipes.get(1).contains("\"heat\":\"heated\""),
                "the mixing recipe data must carry its heat field");
    }
}
