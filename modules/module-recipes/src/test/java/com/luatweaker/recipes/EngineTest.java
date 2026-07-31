package com.luatweaker.recipes;

import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.engine.LuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EngineTest {

    private static class MockRecipeService implements IRecipeManagerService {
        final List<String> removedOutputs = new ArrayList<>();
        final List<String> removedInputs = new ArrayList<>();
        final List<String> removedIds = new ArrayList<>();
        final List<String> removedTags = new ArrayList<>();
        boolean removeAllCalled = false;
        
        final List<String> addedShapeless = new ArrayList<>();
        final List<String> addedShaped = new ArrayList<>();
        
        final List<String> replacedInputs = new ArrayList<>();
        final List<String> replacedOutputs = new ArrayList<>();

        @Override
        public void removeByOutput(String output) {
            removedOutputs.add(output);
        }

        @Override
        public void removeByInput(String input) {
            removedInputs.add(input);
        }

        @Override
        public void removeById(String id) {
            removedIds.add(id);
        }

        @Override
        public void removeAll() {
            removeAllCalled = true;
        }

        @Override
        public void removeByMod(String modId) {}

        @Override
        public void removeByTag(String tag) {
            removedTags.add(tag);
        }

        @Override
        public void addShapeless(String recipeId, ItemCount output, List<IngredientWrapper> ingredients) {
            addedShapeless.add(recipeId + " -> " + output.itemId() + "x" + output.count());
        }

        @Override
        public void addShaped(String recipeId, ItemCount output, List<String> pattern, Map<String, IngredientWrapper> keys) {
            addedShaped.add(recipeId + " -> " + output.itemId() + "x" + output.count() + " pattern:" + String.join(",", pattern));
        }

        @Override
        public void replaceInput(String target, String replacement) {
            replacedInputs.add(target + " -> " + replacement);
        }

        @Override
        public void replaceOutput(String target, String replacement) {
            replacedOutputs.add(target + " -> " + replacement);
        }

        @Override public void addSmelting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {}
        @Override public void addBlasting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {}
        @Override public void addSmoking(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {}
        @Override public void addCampfire(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {}
        @Override public void addStonecutting(String recipeId, ItemCount output, IngredientWrapper input) {}
        @Override public void addSmithing(String recipeId, ItemCount output, IngredientWrapper template, IngredientWrapper base, IngredientWrapper addition) {}
        @Override public void addAnvil(String recipeId, ItemCount output, IngredientWrapper leftInput, IngredientWrapper rightInput, int expCost) {}
        @Override public void addBrewing(String recipeId, String outputPotion, String inputPotion, IngredientWrapper ingredient) {}
        @Override public void addTrade(String profession, int level, ItemCount buy1, ItemCount buy2, ItemCount sell, int maxUses, int xp) {}
    }

    private MockRecipeService mockService;

    @BeforeEach
    public void setup() {
        mockService = new MockRecipeService();
        LuaServiceRegistry.clear();
        
        com.luatweaker.api.pal.Platform.set(new com.luatweaker.api.pal.IPlatformHelper() {
            @Override
            public com.luatweaker.api.objects.IItem createItem(String itemId, int count) {
                return new com.luatweaker.api.objects.IItem() {
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
            public boolean tagExists(String tagId) { return true; }
            @Override
            public List<com.luatweaker.api.objects.IRecipe> getAllRecipes() { return List.of(); }
        });
    }

    @AfterAll
    public static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    public void testRecipeScripts() throws IOException {
        com.luatweaker.api.vm.ILuaEngine engine = new com.luatweaker.core.vm.CobaltLuaEngine();
        com.luatweaker.api.vm.ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(recipesTable, mockService);
        engine.registerService("Recipes", recipesTable);
        
        // Run recipe test script
        File script = findScriptFile("lua/server/recipe_test.lua");
        engine.executeScript(script, "TEST-SERVER");

        // Assert removal methods
        assertTrue(mockService.removedOutputs.contains("minecraft:diamond_sword"));
        assertTrue(mockService.removedInputs.contains("minecraft:netherite_scrap"));
        assertTrue(mockService.removedIds.contains("minecraft:cake"));

        // Assert shapeless addition
        assertTrue(mockService.addedShapeless.size() >= 1);
        assertTrue(mockService.addedShapeless.contains("luatweaker:instant_bread -> minecraft:breadx4"));

        // Assert shaped addition
        assertEquals(1, mockService.addedShaped.size());
        assertEquals("luatweaker:custom_iron_sword -> minecraft:iron_swordx1 pattern: I , I , S ", mockService.addedShaped.get(0));

        // Assert replacements
        assertTrue(mockService.replacedInputs.contains("minecraft:coal -> minecraft:charcoal"));
        assertTrue(mockService.replacedOutputs.contains("minecraft:dirt -> minecraft:cobblestone"));
    }

    @Test
    public void testSyntaxErrorTraceback() throws IOException, InterruptedException {
        com.luatweaker.api.vm.ILuaEngine engine = new com.luatweaker.core.vm.CobaltLuaEngine();
        
        File logFile = new File("logs/luatweaker/latest.log");
        if (logFile.exists()) {
            logFile.delete();
        }

        // Run syntax error script
        File script = findScriptFile("lua/test/syntax_error_test.lua");
        engine.executeScript(script, "TEST-SERVER");

        // Wait for async file logger
        Thread.sleep(1000);

        assertTrue(logFile.exists(), "Log file should be created");
        String logContent = Files.readString(logFile.toPath());
        
        System.out.println("--- GATHERED FANCY LOG OUTPUT ---");
        System.out.println(logContent);
        System.out.println("---------------------------------");

        assertTrue(logContent.contains("LUA SYNTAX ERROR (COMPILE TIME)"));
        assertTrue(logContent.contains("syntax_error_test.lua"));
        assertTrue(logContent.contains("Line Number : 5"));
        assertTrue(logContent.contains("Testing syntax error traceback representation"));
        assertTrue(logContent.contains("========"));
    }

    @Test
    public void testLspStubGeneration() throws IOException {
        File tempDir = new File("build/tmp/testStubs");
        File stubDir = new File(tempDir, "lua/.luatweaker/stubs");
        File stubFile = new File(stubDir, "luatweaker-api.lua");
        if (stubFile.exists()) {
            stubFile.delete();
        }
        
        com.luatweaker.core.lsp.LtvmStubGenerator stubGen = new com.luatweaker.core.lsp.LtvmStubGenerator();
        stubGen.generateClassStub(com.luatweaker.api.recipe.IRecipeManagerService.class, "Recipes");
        
        com.luatweaker.core.lsp.LtvmStubExporter.exportToWorkspace(tempDir.toPath(), stubGen);
        
        assertTrue(stubFile.exists(), "Stub file should be generated");
        
        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("---@meta"));
        assertTrue(stubContent.contains("---@class Mod"));
        assertTrue(stubContent.contains("---@class Recipes"));
        assertTrue(stubContent.contains("Recipes:removeByOutput"));
        assertTrue(stubContent.contains("Recipes:addShaped"));
        assertTrue(stubContent.contains("--- Removes a specific recipe by its registry ID."));
        assertTrue(stubContent.contains("---@param id: string"));
    }

    private File findScriptFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            file = new File("../../" + path);
        }
        return file;
    }
}
