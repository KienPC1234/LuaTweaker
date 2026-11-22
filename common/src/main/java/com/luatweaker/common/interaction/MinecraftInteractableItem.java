package com.luatweaker.common.interaction;

import com.luatweaker.api.interaction.IInteractableItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MinecraftInteractableItem implements IInteractableItem {
    private final Object owner;
    private int slot;
    private final ItemStack directStack;
    private final ItemEnchantmentOps enchantmentOps;

    public MinecraftInteractableItem(@NotNull Object owner, int slot) {
        this(owner, slot, null);
    }

    public MinecraftInteractableItem(@Nullable Object owner, int slot, @Nullable ItemStack directStack) {
        this.owner = owner;
        this.slot = slot;
        this.directStack = directStack;
        this.enchantmentOps = new ItemEnchantmentOps(owner);
    }

    public MinecraftInteractableItem(@NotNull ItemStack directStack) {
        this(null, -1, directStack);
    }

    @Nullable
    public Object getOwner() {
        return owner;
    }

    public ItemStack getItemStack() {
        if (owner instanceof Player player) {
            var inv = player.getInventory();
            if (slot >= 0 && slot < inv.getContainerSize()) {
                return inv.getItem(slot);
            }
        }
        if (directStack != null) {
            return directStack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public String getId() {
        ItemStack stack = getItemStack();
        return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Override
    public int getCount() {
        return getItemStack().getCount();
    }

    @Override
    public void setCount(int count) {
        ItemStack stack = getItemStack();
        if (!stack.isEmpty()) {
            if (count <= 0) {
                if (owner instanceof Player player && slot >= 0 && slot < player.getInventory().getContainerSize()) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                } else {
                    stack.setCount(0);
                }
            } else {
                stack.setCount(count);
            }
        }
    }

    @Override
    public int getSlot() {
        return slot;
    }

    @Override
    public void setSlot(int newSlot) {
        if (owner instanceof Player player) {
            var inv = player.getInventory();
            if (newSlot >= 0 && newSlot < inv.getContainerSize()) {
                ItemStack current = inv.getItem(slot);
                ItemStack target = inv.getItem(newSlot);
                inv.setItem(newSlot, current);
                inv.setItem(slot, target);
                this.slot = newSlot;
            }
        }
    }

    @Override
    @Nullable
    public String getOwnerUuid() {
        if (owner instanceof Player player) {
            return player.getUUID().toString();
        }
        return null;
    }

    @Override
    public boolean useItem(@NotNull Object actorEntity) {
        return actorEntity instanceof Player player && InventoryInteractionHelper.useStack(player, getItemStack());
    }

    @Override
    public boolean drop(@NotNull Object actorEntity, int count) {
        if (owner instanceof Player player) {
            var inv = player.getInventory();
            ItemStack stack = (slot >= 0 && slot < inv.getContainerSize()) ? inv.getItem(slot) : directStack;
            if (stack != null && !stack.isEmpty()) {
                int toDrop = Math.min(count, stack.getCount());
                ItemStack dropStack = stack.split(toDrop);
                player.drop(dropStack, false);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getDamage() {
        ItemStack stack = getItemStack();
        return stack.isDamageableItem() ? stack.getDamageValue() : 0;
    }

    @Override
    public void setDamage(int damage) {
        ItemStack stack = getItemStack();
        if (stack.isDamageableItem()) {
            stack.setDamageValue(damage);
        }
    }

    @Override
    public int getMaxDamage() {
        ItemStack stack = getItemStack();
        return stack.getMaxDamage();
    }

    @Override
    @Nullable
    public String getCustomName() {
        ItemStack stack = getItemStack();
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ?
            stack.getHoverName().getString() : null;
    }

    @Override
    public void setCustomName(@NotNull String name) {
        ItemStack stack = getItemStack();
        if (!stack.isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
        }
    }

    @Override
    public boolean isDamageable() {
        return getItemStack().isDamageableItem();
    }

    @Override
    public boolean isEnchanted() {
        return getItemStack().isEnchanted();
    }

    @Override
    public @NotNull Map<String, Integer> getEnchantments() {
        return enchantmentOps.all(getItemStack());
    }

    @Override
    public int getEnchantmentLevel(@NotNull String enchantmentId) {
        return enchantmentOps.level(getItemStack(), enchantmentId);
    }

    @Override
    public boolean applyEnchantment(@NotNull String enchantmentId, int level) {
        return enchantmentOps.apply(getItemStack(), enchantmentId, level);
    }

    @Override
    public boolean removeEnchantment(@NotNull String enchantmentId) {
        return enchantmentOps.remove(getItemStack(), enchantmentId);
    }

    @Override
    @NotNull
    public String getNbt() {
        ItemStack stack = getItemStack();
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                return customData.copyTag().toString();
            }
        }
        return "{}";
    }

    @Override
    public void setNbt(@NotNull String nbtJson) {
        ItemStackAccess.setNbt(getItemStack(), nbtJson);
    }

    @Override
    @Nullable
    public Object getCustomData(@NotNull String key) {
        return ItemStackAccess.getCustomData(getItemStack(), key);
    }

    @Override
    public void setCustomData(@NotNull String key, @Nullable Object value) {
        ItemStackAccess.setCustomData(getItemStack(), key, value);
    }

    @Override
    @Nullable
    public String getAttribute(@NotNull String key) {
        return ItemStackAccess.getAttribute(getItemStack(), key);
    }

    @Override
    public void setAttribute(@NotNull String key, @NotNull String value) {
        ItemStackAccess.setAttribute(getItemStack(), key, value);
    }

    @Override
    public @NotNull java.util.List<String> getLore() {
        ItemStack stack = getItemStack();
        var itemLore = stack.get(net.minecraft.core.component.DataComponents.LORE);
        if (itemLore == null) {
            return java.util.List.of();
        }
        java.util.List<String> res = new java.util.ArrayList<>();
        for (net.minecraft.network.chat.Component comp : itemLore.lines()) {
            res.add(comp.getString());
        }
        return res;
    }

    @Override
    public void setLore(@NotNull java.util.List<String> lore) {
        ItemStack stack = getItemStack();
        if (!stack.isEmpty()) {
            java.util.List<net.minecraft.network.chat.Component> components = new java.util.ArrayList<>();
            for (String line : lore) {
                components.add(net.minecraft.network.chat.Component.literal(line));
            }
            stack.set(net.minecraft.core.component.DataComponents.LORE,
                    new net.minecraft.world.item.component.ItemLore(components));
        }
    }

    @Override
    public void addLore(@NotNull String line) {
        java.util.List<String> list = new java.util.ArrayList<>(getLore());
        list.add(line);
        setLore(list);
    }

    @Override
    public @NotNull String getRarity() {
        ItemStack stack = getItemStack();
        return stack.isEmpty() ? "COMMON" : stack.getRarity().name();
    }

    @Override
    public boolean hasGlint() {
        ItemStack stack = getItemStack();
        return !stack.isEmpty() && stack.hasFoil();
    }

    @Override
    public int getMaxStackSize() {
        ItemStack stack = getItemStack();
        return stack.isEmpty() ? 64 : stack.getMaxStackSize();
    }

    @Override
    public int getBurnTime() {
        ItemStack stack = getItemStack();
        return stack.isEmpty() ? 0 : dev.architectury.registry.fuel.FuelRegistry.get(stack);
    }

    @Override
    public boolean isFood() {
        ItemStack stack = getItemStack();
        return !stack.isEmpty() && stack.has(net.minecraft.core.component.DataComponents.FOOD);
    }

    @Override
    public int getNutrition() {
        ItemStack stack = getItemStack();
        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        return food != null ? food.nutrition() : 0;
    }

    @Override
    public float getSaturation() {
        ItemStack stack = getItemStack();
        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        return food != null ? food.saturation() : 0.0f;
    }

    @Override
    public boolean isEmpty() {
        return getItemStack().isEmpty();
    }

    @Override
    public @NotNull IInteractableItem copy() {
        return new MinecraftInteractableItem(getItemStack().copy());
    }

    @Override
    @NotNull
    public Object getRawItemStack() {
        return getItemStack();
    }
}

