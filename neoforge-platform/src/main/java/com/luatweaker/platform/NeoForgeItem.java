package com.luatweaker.platform;

import com.luatweaker.api.objects.IItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

public class NeoForgeItem implements IItem {
    private final ItemStack stack;

    public NeoForgeItem(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public String getId() {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public int getCount() {
        return stack.getCount();
    }

    @Override
    public boolean hasTag(String tagId) {
        try {
            var tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
            return stack.is(tagKey);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Object getRawItemStack() {
        return stack;
    }
}
