package com.luatweaker.common.recipe;

import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.common.support.MinecraftBootstrapExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies recipe interception deduplication, independent MerchantOffers, and anvil matching. */
@ExtendWith(MinecraftBootstrapExtension.class)
class InterceptionDeduplicationTest {

    @AfterEach
    void cleanPending() {
        InterceptionHelper.clearPending();
    }

    @Test
    void populatePendingEventsReplacesPreviousReloadWithoutDuplicates() {
        CommonRecipeManager.AnvilData anvil = new CommonRecipeManager.AnvilData(
                "luatweaker:test_anvil",
                new ItemCount("minecraft:diamond_sword", 1),
                new IngredientWrapper("minecraft:iron_sword"),
                new IngredientWrapper("minecraft:diamond"),
                5);
        CommonRecipeManager.TradeData trade = sampleTrade();
        List<CommonRecipeManager.RecipeModification> mods = List.of(
                new CommonRecipeManager.RecipeModification(
                        CommonRecipeManager.ModificationType.ADD_ANVIL, anvil),
                new CommonRecipeManager.RecipeModification(
                        CommonRecipeManager.ModificationType.ADD_TRADE, trade));

        InterceptionHelper.populatePendingEvents(mods);
        InterceptionHelper.populatePendingEvents(mods);

        assertTrue(InterceptionHelper.getPendingAnvil().size() == 1
                        && InterceptionHelper.getPendingTrades().size() == 1,
                "re-populating pending events on reload must not duplicate anvil or trade entries");
    }

    @Test
    void createOfferProducesIndependentVillagerInstances() {
        CommonRecipeManager.TradeData trade = sampleTrade();

        MerchantOffer villagerOneOffer = CommonInterceptionApplier.createOffer(trade);
        MerchantOffer villagerTwoOffer = CommonInterceptionApplier.createOffer(trade);
        villagerOneOffer.increaseUses();

        assertEquals(0, villagerTwoOffer.getUses(),
                "exhausting uses on one villager's MerchantOffer must not affect other villagers");
    }

    @Test
    void matchAnvilResolvesRegisteredRecipeAndRejectsMismatch() {
        CommonRecipeManager.AnvilData anvil = new CommonRecipeManager.AnvilData(
                "luatweaker:gilded_sword",
                new ItemCount("minecraft:golden_sword", 1),
                new IngredientWrapper("minecraft:iron_sword"),
                new IngredientWrapper("minecraft:gold_ingot"),
                3);
        InterceptionHelper.populatePendingEvents(List.of(
                new CommonRecipeManager.RecipeModification(
                        CommonRecipeManager.ModificationType.ADD_ANVIL, anvil)));

        Optional<CommonInterceptionApplier.AnvilMatch> hit = CommonInterceptionApplier.matchAnvil(
                new ItemStack(Items.IRON_SWORD), new ItemStack(Items.GOLD_INGOT));
        Optional<CommonInterceptionApplier.AnvilMatch> miss = CommonInterceptionApplier.matchAnvil(
                new ItemStack(Items.IRON_SWORD), new ItemStack(Items.COAL));

        assertTrue(hit.isPresent()
                        && hit.get().output().is(Items.GOLDEN_SWORD)
                        && hit.get().expCost() == 3
                        && miss.isEmpty(),
                "matchAnvil must match registered inputs and return empty on non-matching inputs");
    }

    private static CommonRecipeManager.TradeData sampleTrade() {
        return new CommonRecipeManager.TradeData(
                "minecraft:farmer",
                1,
                new ItemCount("minecraft:emerald", 2),
                null,
                new ItemCount("minecraft:bread", 6),
                12,
                5);
    }
}
