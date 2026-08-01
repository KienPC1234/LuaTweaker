package com.luatweaker.platform.content;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.pal.IPlatformContent;
import com.luatweaker.platform.NeoForgeItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NeoForgeContentPlatform implements IPlatformContent {
    @Override
    public IItem createItem(String itemId, int count) {
        ResourceLocation rl = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        return new NeoForgeItem(new ItemStack(item, count));
    }

    @Override
    public boolean itemExists(String itemId) {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemId));
    }

    @Override
    public boolean blockExists(String blockId) {
        return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(blockId));
    }

    @Override
    public boolean fluidExists(String fluidId) {
        return BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(fluidId));
    }

    @Override
    public boolean tagExists(String tagId) {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
        return BuiltInRegistries.ITEM.getTag(tagKey).isPresent();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT;
    }

    @Override
    public boolean isDedicatedServer() {
        return net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER;
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public Set<String> getSupportedMobParents() {
        return com.luatweaker.platform.content.MobParentRegistry.getSupportedMobs();
    }

    @Override
    public List<IRecipe> getAllRecipes() {
        List<IRecipe> list = new ArrayList<>();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                list.add(new IRecipe() {
                    @Override
                    public String getId() {
                        return holder.id().toString();
                    }

                    @Override
                    public String getType() {
                        return holder.value().getType().toString();
                    }

                    @Override
                    public List<com.luatweaker.api.wrapper.IngredientWrapper> getIngredients() {
                        return List.of();
                    }

                    @Override
                    public IItem getResult() {
                        var registries = server.registryAccess();
                        return new NeoForgeItem(holder.value().getResultItem(registries));
                    }

                    @Override
                    public Object getRawRecipe() {
                        return holder.value();
                    }
                });
            }
        }
        return list;
    }
}
