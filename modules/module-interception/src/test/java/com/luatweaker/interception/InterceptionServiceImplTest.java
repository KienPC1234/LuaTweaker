package com.luatweaker.interception;

import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptionServiceImplTest {
    private RecordingRecipeService delegate;
    private InterceptionServiceImpl service;

    @BeforeEach
    void setUp() {
        LuaServiceRegistry.clear();
        delegate = new RecordingRecipeService();
        service = new InterceptionServiceImpl(delegate);
    }

    @AfterEach
    void tearDown() {
        LuaServiceRegistry.clear();
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void addAnvilRecipeDelegatesExactConvertedArguments() {
        service.addAnvilRecipe(
            "test_anvil", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:golden_apple", 5);

        assertEquals(1, delegate.anvilCalls.size());
        AnvilCall call = delegate.anvilCalls.get(0);
        assertEquals("test_anvil", call.recipeId());
        assertEquals("minecraft:golden_apple", call.output().itemId());
        assertEquals(1, call.output().count());
        assertEquals("minecraft:iron_ingot", call.left().descriptor());
        assertEquals("minecraft:gold_ingot", call.right().descriptor());
        assertEquals(5, call.expCost());
        assertEquals(1, service.getRegistrationCount());
        assertEquals(0, delegate.brewingCalls.size());
        assertEquals(0, delegate.tradeCalls.size());
    }

    @Test
    void addBrewingRecipeDelegatesExactConvertedArguments() {
        service.addBrewingRecipe(
            "test_brew", "minecraft:water", "minecraft:glowstone_dust", "minecraft:thick");

        assertEquals(1, delegate.brewingCalls.size());
        BrewingCall call = delegate.brewingCalls.get(0);
        assertEquals("test_brew", call.recipeId());
        assertEquals("minecraft:thick", call.outputPotion());
        assertEquals("minecraft:water", call.inputPotion());
        assertEquals("minecraft:glowstone_dust", call.ingredient().descriptor());
        assertEquals(1, service.getRegistrationCount());
        assertEquals(0, delegate.anvilCalls.size());
        assertEquals(0, delegate.tradeCalls.size());
    }

    @Test
    void addVillagerTradeDelegatesExactConvertedArguments() {
        service.addVillagerTrade("farmer", 2, "minecraft:emerald", "minecraft:bread", 12, 10);

        assertEquals(1, delegate.tradeCalls.size());
        TradeCall call = delegate.tradeCalls.get(0);
        assertEquals("farmer", call.profession());
        assertEquals(2, call.level());
        assertEquals("minecraft:emerald", call.buy1().itemId());
        assertEquals(1, call.buy1().count());
        assertNull(call.buy2(), "Legacy API has no second buy slot");
        assertEquals("minecraft:bread", call.sell().itemId());
        assertEquals(1, call.sell().count());
        assertEquals(12, call.maxUses());
        assertEquals(10, call.xp());
        assertEquals(1, service.getRegistrationCount());
    }

    @Test
    void blankAndNullTextInputsAreRejectedLoudly() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addAnvilRecipe("", "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:apple", 1));
        assertThrows(IllegalArgumentException.class,
            () -> service.addAnvilRecipe("id", "  ", "minecraft:gold_ingot", "minecraft:apple", 1));
        assertThrows(IllegalArgumentException.class,
            () -> service.addAnvilRecipe("id", "minecraft:iron_ingot", null, "minecraft:apple", 1));
        assertThrows(IllegalArgumentException.class,
            () -> service.addAnvilRecipe("id", "minecraft:iron_ingot", "minecraft:gold_ingot", "\t", 1));

        assertThrows(IllegalArgumentException.class,
            () -> service.addBrewingRecipe("id", "", "minecraft:glowstone_dust", "minecraft:thick"));
        assertThrows(IllegalArgumentException.class,
            () -> service.addBrewingRecipe("id", "minecraft:water", null, "minecraft:thick"));
        assertThrows(IllegalArgumentException.class,
            () -> service.addBrewingRecipe("id", "minecraft:water", "minecraft:glowstone_dust", " "));

        assertThrows(IllegalArgumentException.class,
            () -> service.addVillagerTrade(null, 1, "minecraft:emerald", "minecraft:bread", 12, 10));
        assertThrows(IllegalArgumentException.class,
            () -> service.addVillagerTrade("farmer", 1, "", "minecraft:bread", 12, 10));
        assertThrows(IllegalArgumentException.class,
            () -> service.addVillagerTrade("farmer", 1, "minecraft:emerald", "  ", 12, 10));

        assertEquals(0, delegate.anvilCalls.size());
        assertEquals(0, delegate.brewingCalls.size());
        assertEquals(0, delegate.tradeCalls.size());
        assertEquals(0, service.getRegistrationCount());
    }

    @Test
    void negativeNumericInputsAreRejectedLoudly() {
        assertThrows(IllegalArgumentException.class,
            () -> service.addAnvilRecipe("id", "a", "b", "c", -1));
        assertThrows(IllegalArgumentException.class,
            () -> service.addVillagerTrade("farmer", -1, "a", "b", 12, 10));
        assertThrows(IllegalArgumentException.class,
            () -> service.addVillagerTrade("farmer", 1, "a", "b", -1, 10));
        assertThrows(IllegalArgumentException.class,
            () -> service.addVillagerTrade("farmer", 1, "a", "b", 12, -1));

        assertEquals(0, delegate.anvilCalls.size());
        assertEquals(0, delegate.tradeCalls.size());
        assertEquals(0, service.getRegistrationCount());
    }

    @Test
    void delegateFailurePropagatesAndIsNotCountedAsRegistered() {
        delegate.failNextAnvil = true;

        assertThrows(IllegalStateException.class,
            () -> service.addAnvilRecipe("id", "a", "b", "c", 1));
        assertEquals(0, service.getRegistrationCount());

        service.addAnvilRecipe("id2", "a", "b", "c", 2);
        assertEquals(1, delegate.anvilCalls.size());
        assertEquals(1, service.getRegistrationCount());
    }

    @Test
    void compatibilityConstructorResolvesActiveRecipeManagerFromRegistry() {
        LuaServiceRegistry.register(InterceptionServiceImpl.RECIPE_SERVICE_KEY, delegate);
        InterceptionServiceImpl compatibilityService = new InterceptionServiceImpl();

        compatibilityService.addBrewingRecipe("client_brew", "in", "catalyst", "out");

        assertEquals(1, delegate.brewingCalls.size());
        assertEquals("client_brew", delegate.brewingCalls.get(0).recipeId());
        assertEquals(1, compatibilityService.getRegistrationCount());
    }

    @Test
    void compatibilityConstructorFailsLoudlyWhenNoRecipeManagerIsRegistered() {
        InterceptionServiceImpl compatibilityService = new InterceptionServiceImpl();

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> compatibilityService.addVillagerTrade("farmer", 1, "a", "b", 12, 10));

        assertTrue(error.getMessage().contains(InterceptionServiceImpl.RECIPE_SERVICE_KEY));
        assertEquals(0, compatibilityService.getRegistrationCount());
    }

    private record AnvilCall(String recipeId, ItemCount output, IngredientWrapper left,
                             IngredientWrapper right, int expCost) {}

    private record BrewingCall(String recipeId, String outputPotion, String inputPotion,
                               IngredientWrapper ingredient) {}

    private record TradeCall(String profession, int level, ItemCount buy1, ItemCount buy2,
                             ItemCount sell, int maxUses, int xp) {}

    /** Records only the three operations under test; all others fail loudly if touched. */
    private static final class RecordingRecipeService implements IRecipeManagerService {
        final List<AnvilCall> anvilCalls = new ArrayList<>();
        final List<BrewingCall> brewingCalls = new ArrayList<>();
        final List<TradeCall> tradeCalls = new ArrayList<>();
        boolean failNextAnvil;

        @Override
        public void addAnvil(String recipeId, ItemCount output, IngredientWrapper leftInput,
                             IngredientWrapper rightInput, int expCost) {
            if (failNextAnvil) {
                failNextAnvil = false;
                throw new IllegalStateException("delegate unavailable");
            }
            anvilCalls.add(new AnvilCall(recipeId, output, leftInput, rightInput, expCost));
        }

        @Override
        public void addBrewing(String recipeId, String outputPotion, String inputPotion,
                               IngredientWrapper ingredient) {
            brewingCalls.add(new BrewingCall(recipeId, outputPotion, inputPotion, ingredient));
        }

        @Override
        public void addTrade(String profession, int level, ItemCount buy1, ItemCount buy2,
                             ItemCount sell, int maxUses, int xp) {
            tradeCalls.add(new TradeCall(profession, level, buy1, buy2, sell, maxUses, xp));
        }

        @Override public void removeByOutput(String output) { unexpected("removeByOutput"); }
        @Override public void removeByInput(String input) { unexpected("removeByInput"); }
        @Override public void removeById(String id) { unexpected("removeById"); }
        @Override public void removeAll() { unexpected("removeAll"); }
        @Override public void removeByMod(String modId) { unexpected("removeByMod"); }
        @Override public void removeByTag(String tag) { unexpected("removeByTag"); }
        @Override public void replaceInput(String target, String replacement) { unexpected("replaceInput"); }
        @Override public void replaceOutput(String target, String replacement) { unexpected("replaceOutput"); }

        @Override
        public void addShapeless(String recipeId, ItemCount output, List<IngredientWrapper> ingredients) {
            unexpected("addShapeless");
        }

        @Override
        public void addShaped(String recipeId, ItemCount output, List<String> pattern,
                              Map<String, IngredientWrapper> keys) {
            unexpected("addShaped");
        }

        @Override
        public void addSmelting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
            unexpected("addSmelting");
        }

        @Override
        public void addBlasting(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
            unexpected("addBlasting");
        }

        @Override
        public void addSmoking(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
            unexpected("addSmoking");
        }

        @Override
        public void addCampfire(String recipeId, ItemCount output, IngredientWrapper input, float xp, int cookTime) {
            unexpected("addCampfire");
        }

        @Override
        public void addStonecutting(String recipeId, ItemCount output, IngredientWrapper input) {
            unexpected("addStonecutting");
        }

        @Override
        public void addSmithing(String recipeId, ItemCount output, IngredientWrapper template,
                                IngredientWrapper base, IngredientWrapper addition) {
            unexpected("addSmithing");
        }

        private static void unexpected(String method) {
            throw new UnsupportedOperationException("Unexpected call to " + method + " in this test");
        }
    }
}
