package com.luatweaker.recipes;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies recipe removals driven by the shared recipe fixture script.
 */
class RecipeRemovalTest extends RecipeScriptFixture {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void removesByOutput() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertTrue(recipeHandler.removedOutputs.contains("minecraft:diamond_sword"),
                "the fixture script must remove the diamond sword by output");
    }

    @Test
    void removesByInput() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertTrue(recipeHandler.removedInputs.contains("minecraft:netherite_scrap"),
                "the fixture script must remove recipes by netherite scrap input");
    }

    @Test
    void removesById() {
        ILuaEngine engine = newBoundEngine();
        runRecipeScript(engine);

        assertTrue(recipeHandler.removedIds.contains("minecraft:cake"),
                "the fixture script must remove the cake recipe by id");
    }
}
