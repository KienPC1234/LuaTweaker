package com.luatweaker.interception;

import com.luatweaker.api.interception.IInterceptionService;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Thin façade that converts the legacy Interception string API into real recipe-manager calls.
 * No definition is kept here: the recipe manager is the single source of truth.
 */
public class InterceptionServiceImpl implements IInterceptionService {
    /** Registry key under which the active recipe manager is published by LuaServiceBootstrap. */
    public static final String RECIPE_SERVICE_KEY = "RecipeManagerServiceImpl";

    private static final int SINGLE_ITEM_COUNT = 1;

    private final Supplier<IRecipeManagerService> recipeServiceSupplier;
    private final AtomicInteger registrationCount = new AtomicInteger();

    /** Preferred constructor: the recipe manager is injected explicitly. */
    public InterceptionServiceImpl(@NotNull IRecipeManagerService recipeService) {
        Objects.requireNonNull(recipeService, "recipeService");
        this.recipeServiceSupplier = () -> recipeService;
    }

    /**
     * Compatibility constructor used by the client-side bootstrap, which cannot inject a
     * recipe manager. Resolution is lazy so the active manager (possibly replaced by a reload)
     * is always used instead of a stale instance captured at construction time.
     */
    public InterceptionServiceImpl() {
        this.recipeServiceSupplier = InterceptionServiceImpl::resolveActiveRecipeService;
    }

    @NotNull
    private static IRecipeManagerService resolveActiveRecipeService() {
        Object registered = LuaServiceRegistry.get(RECIPE_SERVICE_KEY);
        if (registered instanceof IRecipeManagerService recipeService) {
            return recipeService;
        }
        throw new IllegalStateException(
            "No active IRecipeManagerService registered under '" + RECIPE_SERVICE_KEY
                + "'; interception definitions cannot be applied"
        );
    }

    @NotNull
    private IRecipeManagerService recipeService() {
        return recipeServiceSupplier.get();
    }

    @NotNull
    private static String requireText(@NotNull String value, @NotNull String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private static void requireNonNegative(int value, @NotNull String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative: " + value);
        }
    }

    @Override
    public void addAnvilRecipe(@NotNull String recipeId, @NotNull String leftInput, @NotNull String rightInput,
                               @NotNull String outputItem, int expCost) {
        String id = requireText(recipeId, "recipeId");
        String left = requireText(leftInput, "leftInput");
        String right = requireText(rightInput, "rightInput");
        String output = requireText(outputItem, "outputItem");
        requireNonNegative(expCost, "expCost");

        recipeService().addAnvil(
            id,
            new ItemCount(output, SINGLE_ITEM_COUNT),
            new IngredientWrapper(left),
            new IngredientWrapper(right),
            expCost
        );
        registrationCount.incrementAndGet();
    }

    @Override
    public void addBrewingRecipe(@NotNull String recipeId, @NotNull String inputPotion, @NotNull String catalystItem,
                                 @NotNull String outputPotion) {
        String id = requireText(recipeId, "recipeId");
        String input = requireText(inputPotion, "inputPotion");
        String catalyst = requireText(catalystItem, "catalystItem");
        String output = requireText(outputPotion, "outputPotion");

        recipeService().addBrewing(id, output, input, new IngredientWrapper(catalyst));
        registrationCount.incrementAndGet();
    }

    @Override
    public void addVillagerTrade(@NotNull String profession, int level, @NotNull String buyItem,
                                 @NotNull String sellItem, int maxUses, int xp) {
        String prof = requireText(profession, "profession");
        String buy = requireText(buyItem, "buyItem");
        String sell = requireText(sellItem, "sellItem");
        requireNonNegative(level, "level");
        requireNonNegative(maxUses, "maxUses");
        requireNonNegative(xp, "xp");

        // The legacy API has no second buy slot; the recipe layer represents that as null (see RecipesLuaBinding).
        recipeService().addTrade(
            prof,
            level,
            new ItemCount(buy, SINGLE_ITEM_COUNT),
            null,
            new ItemCount(sell, SINGLE_ITEM_COUNT),
            maxUses,
            xp
        );
        registrationCount.incrementAndGet();
    }

    @Override
    public int getRegistrationCount() {
        return registrationCount.get();
    }
}
