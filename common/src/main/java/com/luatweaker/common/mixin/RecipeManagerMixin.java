package com.luatweaker.common.mixin;

import com.google.gson.JsonElement;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.common.recipe.InterceptionHelper;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.interception.InterceptionServiceImpl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"))
    private void onApplyRecipes(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager,
                                ProfilerFiller profiler, CallbackInfo ci) {
        Object rawRecipeManager = LuaServiceRegistry.get(InterceptionServiceImpl.RECIPE_SERVICE_KEY);
        if (rawRecipeManager instanceof CommonRecipeManager recipeManager) {
            InterceptionHelper.populatePendingEvents(recipeManager.getModifications());
            InterceptionHelper.applyModifications((RecipeManager) (Object) this, recipeManager.getModifications());
        }
    }
}
