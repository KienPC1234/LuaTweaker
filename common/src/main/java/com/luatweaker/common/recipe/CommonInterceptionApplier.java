package com.luatweaker.common.recipe;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.common.util.CallbackFirewall;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared loader-agnostic recipe interception applier (Villager Trades, Brewing,
 * and Anvil recipes) consumed by both NeoForge and Fabric.
 */
public final class CommonInterceptionApplier {

    private static final float TRADE_PRICE_MULTIPLIER = 0.05f;
    private static final List<Runnable> POST_POPULATE_HOOKS = new CopyOnWriteArrayList<>();

    public record AnvilMatch(@NotNull String recipeId, @NotNull ItemStack output, int expCost, int materialCost) {
    }

    private CommonInterceptionApplier() {
    }

    public static void registerPostPopulateHook(@NotNull Runnable hook) {
        POST_POPULATE_HOOKS.add(Objects.requireNonNull(hook, "hook"));
    }

    public static void notifyPostPopulateHooks() {
        for (Runnable hook : POST_POPULATE_HOOKS) {
            CallbackFirewall.run(hook, LogStage.RECIPE_APPLY,
                    () -> "Failed to run post-populate recipe hook");
        }
    }

    /**
     * Builds a fresh {@link MerchantOffer} per villager entity so mutable {@code uses}
     * counters are never shared across villagers in the world.
     */
    @NotNull
    public static MerchantOffer createOffer(@NotNull CommonRecipeManager.TradeData data) {
        Item sellItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.sell().itemId()));
        Item buy1Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.buy1().itemId()));
        ItemStack sellStack = new ItemStack(sellItem, data.sell().count());
        ItemCost buy1Cost = new ItemCost(buy1Item, data.buy1().count());
        ItemCost buy2Cost = data.buy2() != null
                ? new ItemCost(BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.buy2().itemId())),
                        data.buy2().count())
                : null;
        return new MerchantOffer(buy1Cost, Optional.ofNullable(buy2Cost), sellStack,
                data.maxUses(), data.xp(), TRADE_PRICE_MULTIPLIER);
    }

    public static void applyPendingBrewing(@NotNull PotionBrewing.Builder builder) {
        for (CommonRecipeManager.BrewingData data : InterceptionHelper.getPendingBrewing()) {
            Boolean registered = CallbackFirewall.supply(
                    () -> registerBrewing(builder, data), Boolean.FALSE,
                    LogStage.RECIPE_APPLY,
                    () -> "Failed to register brewing recipe '" + data.recipeId() + "'");
            if (Boolean.TRUE.equals(registered)) {
                LuaTweakerLog.get().info(LogStage.RECIPE_APPLY,
                        "[Brewing] Registered: " + data.inputPotion() + " + "
                                + data.ingredient().descriptor() + " -> " + data.outputPotion());
            }
        }
    }

    public static boolean registerBrewing(@NotNull PotionBrewing.Builder builder,
                                          @NotNull CommonRecipeManager.BrewingData data) {
        Holder<Potion> inputHolder = BuiltInRegistries.POTION.getHolder(
                ResourceKey.create(Registries.POTION, ResourceLocation.parse(data.inputPotion()))).orElse(null);
        Holder<Potion> outputHolder = BuiltInRegistries.POTION.getHolder(
                ResourceKey.create(Registries.POTION, ResourceLocation.parse(data.outputPotion()))).orElse(null);
        if (inputHolder == null || outputHolder == null) {
            return false;
        }
        String descriptor = data.ingredient().descriptor();
        if (descriptor == null || descriptor.isBlank()) {
            return false;
        }
        if (descriptor.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(descriptor.substring(1)));
            boolean anyAdded = false;
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                Item item = holder.value();
                if (item != Items.AIR) {
                    builder.addMix(inputHolder, item, outputHolder);
                    anyAdded = true;
                }
            }
            return anyAdded;
        }
        Item catalystItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(descriptor));
        if (catalystItem == Items.AIR) {
            return false;
        }
        builder.addMix(inputHolder, catalystItem, outputHolder);
        return true;
    }

    @NotNull
    public static Optional<AnvilMatch> matchAnvil(@NotNull ItemStack leftStack, @NotNull ItemStack rightStack) {
        if (leftStack.isEmpty() || rightStack.isEmpty()) {
            return Optional.empty();
        }
        for (CommonRecipeManager.AnvilData data : InterceptionHelper.getPendingAnvil()) {
            Ingredient left = parseIngredient(data.leftInput());
            Ingredient right = parseIngredient(data.rightInput());
            if (left.test(leftStack) && right.test(rightStack)) {
                return Optional.of(buildAnvilMatch(data, leftStack));
            }
        }
        return Optional.empty();
    }

    private static AnvilMatch buildAnvilMatch(CommonRecipeManager.AnvilData data, ItemStack leftStack) {
        ItemCount output = data.output();
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(output.itemId()));
        ItemStack outputStack = new ItemStack(item, output.count());
        if (output.customName() != null && !output.customName().isEmpty()) {
            outputStack.set(DataComponents.CUSTOM_NAME, Component.literal(output.customName()));
        } else if (Objects.equals(outputStack.getItem(), leftStack.getItem())) {
            outputStack.applyComponents(leftStack.getComponentsPatch());
        }
        return new AnvilMatch(data.recipeId(), outputStack, data.expCost(), 1);
    }

    private static Ingredient parseIngredient(IngredientWrapper ing) {
        if (ing == null || ing.descriptor() == null || ing.descriptor().isEmpty()) {
            return Ingredient.EMPTY;
        }
        String desc = ing.descriptor();
        if (desc.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(desc.substring(1)));
            return Ingredient.of(tag);
        }
        return Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(desc)));
    }
}
