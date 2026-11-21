package com.luatweaker.recipes;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies recipe additions and replacements driven by the shared recipe
 * fixture script.
 */
class RecipeAdditionTest extends RecipeScriptFixture {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void addsShapelessRecipe() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertFalse(recipeHandler.addedShapeless.isEmpty(),
                "the fixture script must add at least one shapeless recipe");
    }

    @Test
    void addsInstantBreadOutput() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertTrue(recipeHandler.addedShapeless.contains("luatweaker:instant_bread -> minecraft:breadx4"),
                "the fixture script must add instant bread crafting 4 bread");
    }

    @Test
    void addsShapedRecipe() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertEquals(1, recipeHandler.addedShaped.size(),
                "the fixture script must add exactly one shaped recipe");
    }

    @Test
    void recordsShapedPattern() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertEquals("luatweaker:custom_iron_sword -> minecraft:iron_swordx1 pattern: I , I , S ",
                recipeHandler.addedShaped.get(0),
                "the shaped sword recipe must keep its pattern and key layout");
    }

    @Test
    void replacesInputWithCharcoal() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertTrue(recipeHandler.replacedInputs.contains("minecraft:coal -> minecraft:charcoal"),
                "the fixture script must swap coal inputs for charcoal");
    }

    @Test
    void replacesOutputWithCobblestone() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertTrue(recipeHandler.replacedOutputs.contains("minecraft:dirt -> minecraft:cobblestone"),
                "the fixture script must swap dirt outputs for cobblestone");
    }
}
