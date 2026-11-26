package com.luatweaker.recipes;

import java.io.File;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.pal.IPlatformContent;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Shared arrange logic for the recipe binding tests: a recording recipe
 * service, a stub platform (every query answers affirmatively) and helpers
 * to bind an engine and run the shared Lua fixture scripts.
 */
class RecipeScriptFixture {

    static final String TEST_SERVER_CHUNK = "TEST-SERVER";

    MockRecipeHandler recipeHandler;
    IRecipeManagerService recipeService;

    @BeforeEach
    void setUpFixture() {
        recipeHandler = new MockRecipeHandler();
        recipeService = MockRecipeHandler.newMockService(recipeHandler);
        LuaServiceRegistry.clear();

        Platform.setContent(new IPlatformContent() {
            @Override
            public IItem createItem(String itemId, int count) {
                return new IItem() {
                    @Override
                    public String getId() { return itemId; }
                    @Override
                    public int getCount() { return count; }
                    @Override
                    public boolean hasTag(String tagId) { return false; }
                    @Override
                    public Object getRawItemStack() { return null; }
                };
            }
            @Override
            public boolean itemExists(String itemId) { return true; }
            @Override
            public boolean blockExists(String blockId) { return true; }
            @Override
            public boolean fluidExists(String fluidId) { return true; }
            @Override
            public boolean tagExists(String tagId) { return true; }
            @Override
            public boolean isModLoaded(String modId) { return true; }

            @Override
            public boolean isClient() {
                return false;
            }

            @Override
            public boolean isDedicatedServer() {
                return true;
            }

            @Override
            public String getPlatformName() {
                return "Mock";
            }

            @Override
            public Set<String> getSupportedMobParents() { return Set.of(); }
            @Override
            public List<IRecipe> getAllRecipes() { return Collections.emptyList(); }
        });
    }

    ILuaEngine newBoundEngine() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(recipesTable, recipeService);
        engine.registerService("Recipes", recipesTable);
        return engine;
    }

    void runRecipeScript(ILuaEngine engine) {
        File script = findScriptFile("lua/server/recipe_test.lua");
        engine.executeScript(script, TEST_SERVER_CHUNK);
    }

    File findScriptFile(String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        java.net.URL resource = loader != null ? loader.getResource(path) : null;
        if (resource != null) {
            try {
                return new File(resource.toURI());
            } catch (java.net.URISyntaxException e) {
                throw new IllegalStateException("Invalid test resource URI: " + resource, e);
            }
        }
        File file = new File("src/test/resources/" + path);
        if (!file.exists()) {
            file = new File("modules/module-recipes/src/test/resources/" + path);
        }
        return file;
    }
}
