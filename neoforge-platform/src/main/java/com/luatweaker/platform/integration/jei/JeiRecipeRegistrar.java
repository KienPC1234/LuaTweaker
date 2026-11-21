package com.luatweaker.platform.integration.jei;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;
import com.luatweaker.platform.recipe.InterceptionHelper;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import com.luatweaker.platform.util.CallbackFirewall;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the vanilla JEI recipe lists (anvil, brewing) and the custom content info tabs that
 * {@link LuaTweakerJeiPlugin} hands to JEI. Kept out of the plugin class so registration stays
 * a thin wiring layer and each recipe kind is built by its own named method.
 */
final class JeiRecipeRegistrar {

    private JeiRecipeRegistrar() {}

    static void registerAnvil(IRecipeRegistration registration, IVanillaRecipeFactory factory) {
        List<IJeiAnvilRecipe> anvilRecipes = new ArrayList<>();
        for (NeoForgeRecipeManager.AnvilData data : InterceptionHelper.getPendingAnvil()) {
            IJeiAnvilRecipe recipe = anvilRecipe(factory, data);
            if (recipe != null) {
                anvilRecipes.add(recipe);
            }
        }
        if (!anvilRecipes.isEmpty()) {
            registration.addRecipes(RecipeTypes.ANVIL, anvilRecipes);
        }
    }

    @Nullable
    private static IJeiAnvilRecipe anvilRecipe(IVanillaRecipeFactory factory, NeoForgeRecipeManager.AnvilData data) {
        ItemStack left = parseStack(data.leftInput());
        ItemStack right = parseStack(data.rightInput());
        ItemStack output = parseOutput(data.output());
        if (left.isEmpty() || right.isEmpty() || output.isEmpty()) {
            return null;
        }
        return factory.createAnvilRecipe(left, List.of(right), List.of(output));
    }

    static void registerBrewing(IRecipeRegistration registration, IVanillaRecipeFactory factory) {
        List<IJeiBrewingRecipe> brewingRecipes = new ArrayList<>();
        for (NeoForgeRecipeManager.BrewingData data : InterceptionHelper.getPendingBrewing()) {
            IJeiBrewingRecipe recipe = brewingRecipe(factory, data);
            if (recipe != null) {
                brewingRecipes.add(recipe);
            }
        }
        if (!brewingRecipes.isEmpty()) {
            registration.addRecipes(RecipeTypes.BREWING, brewingRecipes);
        }
    }

    /**
     * One brewing recipe. A malformed potion id or an unknown item throws while parsing, so
     * the whole build goes through {@link CallbackFirewall}: one bad Lua-registered brewing
     * entry must not abort JEI registration for every other recipe.
     */
    @Nullable
    private static IJeiBrewingRecipe brewingRecipe(IVanillaRecipeFactory factory,
                                                   NeoForgeRecipeManager.BrewingData data) {
        return CallbackFirewall.supply(() -> buildBrewingRecipe(factory, data), null, LogStage.SYSTEM,
                () -> "Ignored invalid JEI brewing recipe for output potion '" + data.outputPotion() + "'");
    }

    @Nullable
    private static IJeiBrewingRecipe buildBrewingRecipe(IVanillaRecipeFactory factory,
                                                        NeoForgeRecipeManager.BrewingData data) {
        String desc = data.ingredient().descriptor();
        String itemId = desc.startsWith("#") ? desc.substring(1) : desc;
        Item catalystItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        ItemStack catalystStack = new ItemStack(catalystItem);

        var inputHolderOpt = BuiltInRegistries.POTION.getHolder(
                ResourceKey.create(Registries.POTION, ResourceLocation.parse(data.inputPotion())));
        var outputHolderOpt = BuiltInRegistries.POTION.getHolder(
                ResourceKey.create(Registries.POTION, ResourceLocation.parse(data.outputPotion())));

        if (!inputHolderOpt.isPresent() || !outputHolderOpt.isPresent() || catalystStack.isEmpty()) {
            return null;
        }
        ItemStack inputPotion = PotionContents.createItemStack(Items.POTION, inputHolderOpt.get());
        ItemStack outputPotion = PotionContents.createItemStack(Items.POTION, outputHolderOpt.get());
        return factory.createBrewingRecipe(List.of(catalystStack), inputPotion, outputPotion);
    }

    /** Item &amp; block information tabs for the tooltips registered on Lua content. */
    static void registerItemInfo(IRecipeRegistration registration) {
        Object contentObj = LuaServiceRegistry.get("Content");
        if (!(contentObj instanceof IContentService contentService)) {
            return;
        }
        for (IItemBuilder item : contentService.getRegisteredItems()) {
            if (item.getTooltips().isEmpty()) {
                continue;
            }
            String rawId = item.getId();
            String fullId = rawId.contains(":") ? rawId : (LuaTweakerMod.MODID + ":" + rawId);
            ResourceLocation rl = ResourceLocation.tryParse(fullId);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                continue;
            }
            Item mcItem = BuiltInRegistries.ITEM.get(rl);
            List<Component> lines = item.getTooltips().stream()
                    .map(line -> (Component) Component.literal(line))
                    .toList();
            registration.addIngredientInfo(mcItem, lines.toArray(new Component[0]));
        }
    }

    /** Recipes of every custom Lua category. */
    static void registerCustomRecipes(IRecipeRegistration registration) {
        for (CustomJeiRegistry.CustomCategorySpec spec : CustomJeiRegistry.getCategories()) {
            RecipeType<CustomJeiRegistry.CustomRecipeEntry> recipeType = RecipeType.create(
                    spec.uid().getNamespace(), spec.uid().getPath(), CustomJeiRegistry.CustomRecipeEntry.class);
            List<CustomJeiRegistry.CustomRecipeEntry> recipes = CustomJeiRegistry.getRecipesForCategory(spec.uid());
            if (!recipes.isEmpty()) {
                registration.addRecipes(recipeType, recipes);
            }
        }
    }

    private static ItemStack parseStack(IngredientWrapper ing) {
        if (ing == null || ing.descriptor() == null) { return ItemStack.EMPTY; }
        String desc = ing.descriptor();
        String itemId = desc.startsWith("#") ? desc.substring(1) : desc;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        return new ItemStack(item);
    }

    private static ItemStack parseOutput(ItemCount data) {
        if (data == null) { return ItemStack.EMPTY; }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.itemId()));
        ItemStack stack = new ItemStack(item, data.count());
        if (data.customName() != null && !data.customName().isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(data.customName()));
        }
        return stack;
    }
}
