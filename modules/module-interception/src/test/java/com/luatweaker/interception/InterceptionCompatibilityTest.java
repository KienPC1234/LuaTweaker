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
 * The no-argument constructor resolves the active recipe manager from the service registry.
 */
class InterceptionCompatibilityTest {

    private static final String BREW_ID = "client_brew";

    private RecordingRecipeService delegate;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        LuaServiceRegistry.clear();
        delegate = new RecordingRecipeService();
    }

    @AfterEach
    void tearDown() {
        LuaServiceRegistry.clear();
    }

    @Test
    void compatibilityConstructorForwardsToTheRegisteredRecipeManager() {
        LuaServiceRegistry.register(InterceptionServiceImpl.RECIPE_SERVICE_KEY, delegate.asService());
        InterceptionServiceImpl compatibilityService = new InterceptionServiceImpl();

        compatibilityService.addBrewingRecipe(BREW_ID, "in", "catalyst", "out");

        Assertions.assertEquals(List.of("addBrewing"), delegate.methodsCalled(),
                "the compatibility constructor must use the recipe manager from the registry");
    }

    @Test
    void compatibilityConstructorCountsItsRegistrations() {
        LuaServiceRegistry.register(InterceptionServiceImpl.RECIPE_SERVICE_KEY, delegate.asService());
        InterceptionServiceImpl compatibilityService = new InterceptionServiceImpl();

        compatibilityService.addBrewingRecipe(BREW_ID, "in", "catalyst", "out");

        Assertions.assertEquals(1, compatibilityService.getRegistrationCount(),
                "a registration through the compatibility constructor must be counted");
    }

    @Test
    void compatibilityConstructorFailsLoudlyWithoutARecipeManager() {
        InterceptionServiceImpl compatibilityService = new InterceptionServiceImpl();

        Assertions.assertAll("a missing recipe manager must fail loudly without registering",
            () -> Assertions.assertThrows(IllegalStateException.class,
                () -> compatibilityService.addVillagerTrade("farmer", 1, "a", "b", 12, 10),
                "calling the compatibility service without a registered recipe manager must fail"),
            () -> Assertions.assertEquals(0, compatibilityService.getRegistrationCount(),
                "a failed compatibility call must not be counted"));
    }

    @Test
    void compatibilityFailureMentionsTheMissingServiceKey() {
        IllegalStateException error = missingManagerFailure();

        Assertions.assertTrue(error.getMessage() != null
                        && error.getMessage().contains(InterceptionServiceImpl.RECIPE_SERVICE_KEY),
                "the error must name the missing registry key, got: " + error.getMessage());
    }

    private IllegalStateException missingManagerFailure() {
        InterceptionServiceImpl compatibilityService = new InterceptionServiceImpl();
        return Assertions.assertThrows(IllegalStateException.class,
                () -> compatibilityService.addVillagerTrade("farmer", 1, "a", "b", 12, 10),
                "calling the compatibility service without a registered recipe manager must propagate");
    }
}
