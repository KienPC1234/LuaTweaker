package com.luatweaker.platform.integration.jei;

import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.platform.LuaTweakerMod;
import com.luatweaker.platform.recipe.InterceptionHelper;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI (Just Enough Items) integration plugin for LuaTweaker.
 *
 * <p>Shaped, shapeless, and workstation recipes injected into RecipeManager are detected automatically.
 * Anvil and Brewing recipes are registered explicitly into JEI's ANVIL and BREWING recipe categories.</p>
 */
@JeiPlugin
public class LuaTweakerJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(LuaTweakerMod.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        IVanillaRecipeFactory factory = registration.getVanillaRecipeFactory();

        // 1. Register Anvil Recipes in JEI
        List<IJeiAnvilRecipe> anvilRecipes = new ArrayList<>();
        for (NeoForgeRecipeManager.AnvilData data : InterceptionHelper.getPendingAnvil()) {
            ItemStack left = parseStack(data.leftInput());
            ItemStack right = parseStack(data.rightInput());
            ItemStack output = parseOutput(data.output());
            if (!left.isEmpty() && !right.isEmpty() && !output.isEmpty()) {
                IJeiAnvilRecipe recipe = factory.createAnvilRecipe(left, List.of(right), List.of(output));
                if (recipe != null) {
                    anvilRecipes.add(recipe);
                }
            }
        }
        if (!anvilRecipes.isEmpty()) {
            registration.addRecipes(RecipeTypes.ANVIL, anvilRecipes);
        }

        // 2. Register Brewing Recipes in JEI
        List<IJeiBrewingRecipe> brewingRecipes = new ArrayList<>();
        for (NeoForgeRecipeManager.BrewingData data : InterceptionHelper.getPendingBrewing()) {
            try {
                String desc = data.ingredient().descriptor();
                String itemId = desc.startsWith("#") ? desc.substring(1) : desc;
                Item catalystItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                ItemStack catalystStack = new ItemStack(catalystItem);

                var inputHolderOpt = BuiltInRegistries.POTION.getHolder(ResourceKey.create(Registries.POTION, ResourceLocation.parse(data.inputPotion())));
                var outputHolderOpt = BuiltInRegistries.POTION.getHolder(ResourceKey.create(Registries.POTION, ResourceLocation.parse(data.outputPotion())));

                if (inputHolderOpt.isPresent() && outputHolderOpt.isPresent() && !catalystStack.isEmpty()) {
                    ItemStack inputPotion = PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, inputHolderOpt.get());
                    ItemStack outputPotion = PotionContents.createItemStack(net.minecraft.world.item.Items.POTION, outputHolderOpt.get());
                    IJeiBrewingRecipe brewingRecipe = factory.createBrewingRecipe(List.of(catalystStack), inputPotion, outputPotion);
                    if (brewingRecipe != null) {
                        brewingRecipes.add(brewingRecipe);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (!brewingRecipes.isEmpty()) {
            registration.addRecipes(RecipeTypes.BREWING, brewingRecipes);
        }
    }

    private static ItemStack parseStack(IngredientWrapper ing) {
        if (ing == null || ing.descriptor() == null) return ItemStack.EMPTY;
        String desc = ing.descriptor();
        String itemId = desc.startsWith("#") ? desc.substring(1) : desc;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return new ItemStack(item);
    }

    private static ItemStack parseOutput(ItemCount data) {
        if (data == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.itemId()));
        ItemStack stack = new ItemStack(item, data.count());
        if (data.customName() != null && !data.customName().isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(data.customName()));
        }
        return stack;
    }
}
