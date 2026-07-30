package com.luatweaker.platform.integration.jei;

import com.luatweaker.platform.LuaTweakerMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI (Just Enough Items) integration plugin for LuaTweaker.
 *
 * <p>Since LuaTweaker injects all shaped, shapeless, and modified recipes directly
 * into Minecraft's central {@link net.minecraft.world.item.crafting.RecipeManager},
 * JEI's default recipe locator will automatically detect and show all added/edited recipes
 * in the JEI item list and recipe lookup overlays.</p>
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
        // JEI automatically polls Minecraft's RecipeManager, meaning additions/deletions/replacements
        // injected by LuaTweaker are displayed out-of-the-box!
    }
}
