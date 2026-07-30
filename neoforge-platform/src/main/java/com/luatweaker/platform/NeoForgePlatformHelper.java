package com.luatweaker.platform;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.pal.IPlatformHelper;
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

public class NeoForgePlatformHelper implements IPlatformHelper {
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
    public boolean tagExists(String tagId) {
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
        return BuiltInRegistries.ITEM.getTag(tagKey).isPresent();
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
