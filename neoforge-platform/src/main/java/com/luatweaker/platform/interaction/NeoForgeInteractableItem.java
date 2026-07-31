package com.luatweaker.platform.interaction;

import com.luatweaker.api.interaction.IInteractableItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NeoForgeInteractableItem implements IInteractableItem {
    private final Object owner;
    private int slot;

    public NeoForgeInteractableItem(@NotNull Object owner, int slot) {
        this.owner = owner;
        this.slot = slot;
    }

    private ItemStack getItemStack() {
        if (owner instanceof Player player) {
            var inv = player.getInventory();
            if (slot >= 0 && slot < inv.getContainerSize()) {
                return inv.getItem(slot);
            }
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
                if (owner instanceof Player player) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
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
        if (actorEntity instanceof Player player) {
            ItemStack stack = getItemStack();
            if (!stack.isEmpty()) {
                var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                var hit = new net.minecraft.world.phys.BlockHitResult(
                    player.position(), net.minecraft.core.Direction.UP, player.blockPosition(), false
                );
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    var res = serverPlayer.gameMode.useItemOn(serverPlayer, serverPlayer.level(), stack, hand, hit);
                    return res.consumesAction();
                } else {
                    var res = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, hit));
                    return res.consumesAction();
                }
            }
        }
        return false;
    }

    @Override
    public boolean drop(@NotNull Object actorEntity, int count) {
        if (owner instanceof Player player) {
            var inv = player.getInventory();
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty()) {
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
        ItemStack stack = getItemStack();
        if (!stack.isEmpty()) {
            try {
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtJson);
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, t -> t.merge(tag));
            } catch (Exception ignored) {}
        }
    }

    @Override
    @Nullable
    public String getAttribute(@NotNull String key) {
        ItemStack stack = getItemStack();
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains(key)) {
                return customData.copyTag().get(key).getAsString();
            }
        }
        return null;
    }

    @Override
    public void setAttribute(@NotNull String key, @NotNull String value) {
        ItemStack stack = getItemStack();
        if (!stack.isEmpty()) {
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(key, value));
        }
    }

    @Override
    @NotNull
    public Object getRawItemStack() {
        return getItemStack();
    }
}
