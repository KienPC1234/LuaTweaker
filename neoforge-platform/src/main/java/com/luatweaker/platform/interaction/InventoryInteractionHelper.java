package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.platform.NeoForgeLocatedItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public final class InventoryInteractionHelper {
    private InventoryInteractionHelper() {}

    public static boolean performItemUse(@NotNull IEntity actor, int slot) {
        if (actor.getRawEntity() instanceof Player player) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
                var hit = new BlockHitResult(player.position(), net.minecraft.core.Direction.UP, player.blockPosition(), false);
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

    public static boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        if (actor.getRawEntity() instanceof Player player) {
            var inv = player.getInventory();
            if (fromSlot >= 0 && fromSlot < inv.getContainerSize() && toSlot >= 0 && toSlot < inv.getContainerSize()) {
                ItemStack fromStack = inv.getItem(fromSlot);
                ItemStack toStack = inv.getItem(toSlot);
                inv.setItem(fromSlot, toStack);
                inv.setItem(toSlot, fromStack);
                return true;
            }
        }
        return false;
    }

    public static boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        if (actor.getRawEntity() instanceof Player player) {
            var inv = player.getInventory();
            if (slot >= 0 && slot < inv.getContainerSize()) {
                ItemStack stack = inv.getItem(slot);
                if (!stack.isEmpty()) {
                    int toDrop = Math.min(count, stack.getCount());
                    ItemStack dropStack = stack.split(toDrop);
                    player.drop(dropStack, false);
                    return true;
                }
            }
        }
        return false;
    }

    @NotNull
    public static List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        List<ILocatedItem> list = new ArrayList<>();
        if (entity.getRawEntity() instanceof Player player) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    list.add(new NeoForgeLocatedItem(id, stack.getCount(), i, player.getUUID().toString(), null, null, null, stack));
                }
            }
        }
        return list;
    }
}
