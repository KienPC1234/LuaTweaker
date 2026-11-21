package com.luatweaker.recipes;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies ingredient alternatives survive the recipe binding and JEI
 * builders degrade to warnings when no JEI service is registered.
 */
class IngredientAlternativeTest extends RecipeScriptFixture {

    private static final String ALT_CHUNK = "alt_recipe";
    private static final String ALT_SCRIPT =
            "Recipe.Shapeless('alt_test')\n" +
            "    :Inputs({ ingredient('minecraft:stick'):orIngredient('minecraft:blaze_rod')"
                    + ":alt('#minecraft:planks') })\n" +
            "    :Output('minecraft:bread')\n" +
            "    :Register()";
    private static final String JEI_CHUNK = "jei_fallback_test";
    private static final String JEI_SCRIPT =
            "local cat = Recipe.NewJeiCategory('demo_cat')\n" +
            "cat:Title('Demo'):Size(100, 50)\n" +
            "cat:Register()\n" +
            "Recipe.AddJeiRecipe('demo_cat', { inputs = { 'minecraft:iron_ingot' } })\n";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private void runAltScript(ILuaEngine engine) {
        ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(engine, recipesTable, recipeService);
        engine.executeStringOrThrow(ALT_SCRIPT, ALT_CHUNK);
    }

    @Test
    void registersSingleIngredient() {
        ILuaEngine engine = newBoundEngine();
        runAltScript(engine);

        assertEquals(1, recipeHandler.capturedIngredients.size(),
                "one ingredient was registered");
    }

    @Test
    void keepsBaseDescriptor() {
        ILuaEngine engine = newBoundEngine();
        runAltScript(engine);

        IngredientWrapper wrapper = recipeHandler.capturedIngredients.get(0);
        assertEquals("minecraft:stick", wrapper.descriptor(),
                "the base ingredient must stay the stick");
    }

    @Test
    void preservesAlternatives() {
        ILuaEngine engine = newBoundEngine();
        runAltScript(engine);

        IngredientWrapper wrapper = recipeHandler.capturedIngredients.get(0);
        assertEquals(2, wrapper.alternatives().size(),
                "or()/alt() must be preserved for the platform ingredient builder: "
                        + wrapper.alternatives());
    }

    @Test
    void keepsBlazeRodAlternative() {
        ILuaEngine engine = newBoundEngine();
        runAltScript(engine);

        IngredientWrapper wrapper = recipeHandler.capturedIngredients.get(0);
        assertEquals("minecraft:blaze_rod", wrapper.alternatives().get(0).descriptor(),
                "the first alternative must be the blaze rod");
    }

    @Test
    void keepsPlanksAlternative() {
        ILuaEngine engine = newBoundEngine();
        runAltScript(engine);

        IngredientWrapper wrapper = recipeHandler.capturedIngredients.get(0);
        assertEquals("#minecraft:planks", wrapper.alternatives().get(1).descriptor(),
                "the second alternative must be the planks tag");
    }

    @Test
    void jeiBuildersWarnInsteadOfThrowing() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable recipesTable = engine.createTable();
        // MockRecipeHandler implements only IRecipeManagerService: JEI calls
        // must degrade to a warning, never an exception or silent drop.
        RecipesLuaBinding.bind(engine, recipesTable, recipeService);

        assertDoesNotThrow(() -> engine.executeStringOrThrow(JEI_SCRIPT, JEI_CHUNK),
                "JEI calls without a JEI service must degrade to a warning, never throw");
    }
}
