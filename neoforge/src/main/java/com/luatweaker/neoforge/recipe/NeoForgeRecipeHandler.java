package com.luatweaker.neoforge.recipe;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.common.recipe.InterceptionHelper;
import com.luatweaker.common.util.CallbackFirewall;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

public final class NeoForgeRecipeHandler {

    private static final float TRADE_PRICE_MULTIPLIER = 0.05f;

    private NeoForgeRecipeHandler() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NeoForgeRecipeHandler::onRegisterBrewing);
        NeoForge.EVENT_BUS.addListener(NeoForgeRecipeHandler::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(NeoForgeRecipeHandler::onAnvilUpdate);
    }

    public static void onVillagerTrades(VillagerTradesEvent event) {
        for (CommonRecipeManager.TradeData data : InterceptionHelper.getPendingTrades()) {
            ResourceLocation profLoc = ResourceLocation.parse(data.profession());
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(profLoc);
            if (profession == null || !event.getType().equals(profession)) {
                continue;
            }

            var levelMap = event.getTrades();
            if (!levelMap.containsKey(data.level())) {
                continue;
            }

            MerchantOffer offer = offerFor(data);
            levelMap.get(data.level()).add((trader, random) -> offer);
        }
    }

    @NotNull
    private static MerchantOffer offerFor(CommonRecipeManager.TradeData data) {
        ItemStack sellStack = new ItemStack(itemOf(data.sell()), data.sell().count());
        ItemCost buy1Cost = new ItemCost(itemOf(data.buy1()), data.buy1().count());
        ItemCost buy2Cost = data.buy2() != null ? new ItemCost(itemOf(data.buy2()), data.buy2().count()) : null;
        return new MerchantOffer(buy1Cost, Optional.ofNullable(buy2Cost), sellStack,
                data.maxUses(), data.xp(), TRADE_PRICE_MULTIPLIER);
    }

    private static Item itemOf(ItemCount count) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(count.itemId()));
    }

    public static void onRegisterBrewing(RegisterBrewingRecipesEvent event) {
        net.minecraft.world.item.alchemy.PotionBrewing.Builder builder = event.getBuilder();
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

    private static boolean registerBrewing(net.minecraft.world.item.alchemy.PotionBrewing.Builder builder,
                                           CommonRecipeManager.BrewingData data) {
        ResourceLocation inputLoc = ResourceLocation.parse(data.inputPotion());
        ResourceLocation outputLoc = ResourceLocation.parse(data.outputPotion());
        var inputKey = ResourceKey.create(Registries.POTION, inputLoc);
        var outputKey = ResourceKey.create(Registries.POTION, outputLoc);
        var inputHolder = BuiltInRegistries.POTION.getHolder(inputKey).orElse(null);
        var outputHolder = BuiltInRegistries.POTION.getHolder(outputKey).orElse(null);
        if (inputHolder == null || outputHolder == null) {
            return false;
        }
        String descriptor = data.ingredient().descriptor();
        String itemId = descriptor.startsWith("#") ? descriptor.substring(1) : descriptor;
        Item catalystItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (catalystItem == Items.AIR) {
            return false;
        }
        builder.addMix(inputHolder, catalystItem, outputHolder);
        return true;
    }

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        for (CommonRecipeManager.AnvilData data : InterceptionHelper.getPendingAnvil()) {
            Ingredient left = parseIngredient(data.leftInput());
            Ingredient right = parseIngredient(data.rightInput());

            boolean leftMatch = left.test(event.getLeft());
            boolean rightMatch = right.test(event.getRight());

            if (leftMatch && rightMatch) {
                ItemStack outputStack = createItemStack(data.output());
                if (Objects.equals(outputStack.getItem(), event.getLeft().getItem())
                        && (data.output().customName() == null || data.output().customName().isEmpty())) {
                    outputStack.applyComponents(event.getLeft().getComponentsPatch());
                }

                event.setOutput(outputStack);
                event.setCost(data.expCost());
                event.setMaterialCost(1);

                LuaTweakerLog.get().info(LogStage.RECIPE_APPLY,
                        "[Anvil] Applied recipe '" + data.recipeId() + "' -> "
                                + data.output().count() + "x " + data.output().itemId());
                return;
            }
        }
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
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(desc));
        return Ingredient.of(item);
    }

    private static ItemStack createItemStack(ItemCount output) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(output.itemId()));
        ItemStack stack = new ItemStack(item, output.count());
        if (output.customName() != null && !output.customName().isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    net.minecraft.network.chat.Component.literal(output.customName()));
        }
        return stack;
    }
}
