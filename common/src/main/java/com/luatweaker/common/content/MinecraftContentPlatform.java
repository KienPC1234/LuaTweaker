package com.luatweaker.common.content;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.pal.IPlatformContent;
import com.luatweaker.common.MinecraftItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MinecraftContentPlatform implements IPlatformContent {
    @Override
    public IItem createItem(String itemId, int count) {
        ResourceLocation rl = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        return new MinecraftItem(new ItemStack(item, count));
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
        return dev.architectury.platform.Platform.isModLoaded(modId);
    }

    @Override
    public boolean isClient() {
        return dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT;
    }

    @Override
    public boolean isDedicatedServer() {
        return dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.SERVER;
    }

    @Override
    public boolean hasLocalServer() {
        return com.luatweaker.common.CommonServerHolder.get() != null;
    }

    @Override
    public String getPlatformName() {
        return dev.architectury.platform.Platform.isNeoForge() ? "NeoForge" : dev.architectury.platform.Platform.isFabric() ? "Fabric" : "Architectury";
    }

    @Override
    public Set<String> getSupportedMobParents() {
        return MobParentRegistry.getSupportedMobs();
    }

    @Override
    public List<IRecipe> getAllRecipes() {
        List<IRecipe> list = new ArrayList<>();
        collectRecipes(com.luatweaker.common.CommonServerHolder.get(), list);
        return list;
    }

    private static void collectRecipes(net.minecraft.server.MinecraftServer server, List<IRecipe> list) {
        if (server == null) {
            return;
        }
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            list.add(new RecipeView(holder, server));
        }
    }

    /** Read-only Lua view of one loaded recipe. */
    private static final class RecipeView implements IRecipe {
        private final RecipeHolder<?> holder;
        private final net.minecraft.server.MinecraftServer server;

        RecipeView(RecipeHolder<?> holder, net.minecraft.server.MinecraftServer server) {
            this.holder = holder;
            this.server = server;
        }

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
            return new MinecraftItem(holder.value().getResultItem(server.registryAccess()));
        }

        @Override
        public Object getRawRecipe() {
            return holder.value();
        }
    }
}
