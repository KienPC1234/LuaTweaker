package com.luatweaker.interception;

import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Input validation and failure semantics of the interception service.
 */
class InterceptionServiceImplTest {

    private static final String ANVIL_ID = "test_anvil";
    private static final String BREW_ID = "test_brew";
    private static final String IRON = "minecraft:iron_ingot";
    private static final String GOLD = "minecraft:gold_ingot";
    private static final String APPLE = "minecraft:golden_apple";
    private static final String WATER = "minecraft:water";
    private static final String GLOWSTONE = "minecraft:glowstone_dust";
    private static final String THICK = "minecraft:thick";
    private static final String FARMER = "farmer";
    private static final String EMERALD = "minecraft:emerald";
    private static final String BREAD = "minecraft:bread";

    private RecordingRecipeService delegate;
    private InterceptionServiceImpl service;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        LuaServiceRegistry.clear();
        delegate = new RecordingRecipeService();
        service = new InterceptionServiceImpl(delegate.asService());
    }

    @AfterEach
    void tearDown() {
        LuaServiceRegistry.clear();
    }

    @Test
    void blankAndNullTextInputsAreRejected() {
        Assertions.assertAll("every blank or null text input must be rejected",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addAnvilRecipe("", IRON, GOLD, APPLE, 1), "a blank recipe id must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addAnvilRecipe(ANVIL_ID, "  ", GOLD, APPLE, 1), "a blank left input must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addAnvilRecipe(ANVIL_ID, IRON, null, APPLE, 1), "a null right input must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addAnvilRecipe(ANVIL_ID, IRON, GOLD, "\t", 1), "a whitespace output must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addBrewingRecipe(BREW_ID, "", GLOWSTONE, THICK), "a blank potion id must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addBrewingRecipe(BREW_ID, WATER, null, THICK), "a null catalyst must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addBrewingRecipe(BREW_ID, WATER, GLOWSTONE, " "), "a blank output potion must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addVillagerTrade(null, 1, EMERALD, BREAD, 12, 10), "a null profession must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addVillagerTrade(FARMER, 1, "", BREAD, 12, 10), "a blank buy item must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addVillagerTrade(FARMER, 1, EMERALD, "  ", 12, 10), "a blank sell item must be rejected"));
    }

    @Test
    void rejectedTextInputsRegisterNothing() {
        Assertions.assertAll("a rejected input must not reach the delegate or the counter",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addAnvilRecipe("", IRON, GOLD, APPLE, 1), "a blank recipe id must be rejected"),
            () -> Assertions.assertEquals(List.of(), delegate.methodsCalled(),
                "no delegate call may happen for a rejected input"),
            () -> Assertions.assertEquals(0, service.getRegistrationCount(),
                "a rejected registration must not increment the counter"));
    }

    @Test
    void negativeNumericInputsAreRejected() {
        Assertions.assertAll("every negative numeric input must be rejected and register nothing",
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addAnvilRecipe("id", "a", "b", "c", -1), "a negative exp cost must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addVillagerTrade(FARMER, -1, "a", "b", 12, 10), "a negative level must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addVillagerTrade(FARMER, 1, "a", "b", -1, 10), "negative max uses must be rejected"),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.addVillagerTrade(FARMER, 1, "a", "b", 12, -1), "negative xp must be rejected"),
            () -> Assertions.assertEquals(List.of(), delegate.methodsCalled(),
                "no delegate call may happen for a negative numeric input"),
            () -> Assertions.assertEquals(0, service.getRegistrationCount(),
                "no negative numeric registration may be counted"));
    }

    @Test
    void delegateFailurePropagates() {
        delegate.failNextAnvilOnce();

        Assertions.assertThrows(IllegalStateException.class,
                () -> service.addAnvilRecipe("id", "a", "b", "c", 1),
                "a delegate failure must surface instead of being swallowed");
    }

    @Test
    void failedRegistrationIsNotCounted() {
        delegate.failNextAnvilOnce();
        runFailingAnvilRegistration();

        Assertions.assertEquals(0, service.getRegistrationCount(),
                "a registration whose delegate call failed must not be counted");
    }

    @Test
    void registrationAfterAFailureStillSucceeds() {
        delegate.failNextAnvilOnce();
        runFailingAnvilRegistration();

        service.addAnvilRecipe("id2", IRON, GOLD, APPLE, 2);

        Assertions.assertEquals(1, service.getRegistrationCount(),
                "the next successful registration must be counted after an earlier failure");
    }

    private void runFailingAnvilRegistration() {
        Assertions.assertThrows(IllegalStateException.class,
                () -> service.addAnvilRecipe("id", IRON, GOLD, APPLE, 1),
                "the delegate failure must propagate to the caller");
    }
}
