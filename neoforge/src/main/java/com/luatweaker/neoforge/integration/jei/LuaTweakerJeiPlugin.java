package com.luatweaker.neoforge.integration.jei;

import com.luatweaker.common.integration.jei.CustomJeiRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@JeiPlugin
public class LuaTweakerJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath("luatweaker", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        for (CustomJeiRegistry.CustomCategorySpec spec : CustomJeiRegistry.getCategories()) {
            registration.addRecipeCategories(LuaCustomRecipeCategory.create(spec, guiHelper));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (CustomJeiRegistry.CustomCategorySpec spec : CustomJeiRegistry.getCategories()) {
            registerCategoryCatalysts(registration, spec);
        }
    }

    private static void registerCategoryCatalysts(IRecipeCatalystRegistration registration,
                                                  CustomJeiRegistry.CustomCategorySpec spec) {
        RecipeType<CustomJeiRegistry.CustomRecipeEntry> recipeType = RecipeType.create(
                spec.uid().getNamespace(), spec.uid().getPath(), CustomJeiRegistry.CustomRecipeEntry.class);
        for (String catId : spec.catalysts()) {
            ItemStack catalystStack = catalystStack(catId);
            if (catalystStack != null) {
                registration.addRecipeCatalyst(catalystStack, recipeType);
            }
        }
    }

    @Nullable
    private static ItemStack catalystStack(String catalystId) {
        ResourceLocation rl = ResourceLocation.tryParse(catalystId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        IVanillaRecipeFactory factory = registration.getVanillaRecipeFactory();
        JeiRecipeRegistrar.registerAnvil(registration, factory);
        JeiRecipeRegistrar.registerBrewing(registration, factory);
        JeiRecipeRegistrar.registerItemInfo(registration);
        JeiRecipeRegistrar.registerCustomRecipes(registration);
    }
}
