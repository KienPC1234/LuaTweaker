package com.luatweaker.common.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.ILocatedItem;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public final class InventoryInteractionHelper {
    private InventoryInteractionHelper() {}

    public static boolean performItemUse(@NotNull IEntity actor, int slot) {
        return actor.getRawEntity() instanceof Player player
                && useStack(player, player.getInventory().getItem(slot));
    }

    /** Runs the vanilla "right click with this stack" pipeline for an inventory stack. */
    static boolean useStack(Player player, ItemStack stack) {
        return !stack.isEmpty() && (player instanceof ServerPlayer serverPlayer
                ? useOnServer(serverPlayer, stack)
                : useOnClient(player, stack));
    }

    private static boolean useOnServer(ServerPlayer player, ItemStack stack) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        var result = player.gameMode.useItem(player, player.level(), stack, hand);
        if (result.consumesAction()) {
            return true;
        }
        BlockHitResult hit = new BlockHitResult(player.position(), Direction.UP, player.blockPosition(), false);
        return player.gameMode.useItemOn(player, player.level(), stack, hand, hit).consumesAction();
    }

    private static boolean useOnClient(Player player, ItemStack stack) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        var result = stack.use(player.level(), player, hand);
        if (result.getResult().consumesAction()) {
            return true;
        }
        BlockHitResult hit = new BlockHitResult(player.position(), Direction.UP, player.blockPosition(), false);
        return stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, hand, hit)).consumesAction();
    }

    public static boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        if (!(actor.getRawEntity() instanceof Player player)) {
            return false;
        }
        var inventory = player.getInventory();
        if (fromSlot < 0 || fromSlot >= inventory.getContainerSize()
                || toSlot < 0 || toSlot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack fromStack = inventory.getItem(fromSlot);
        ItemStack toStack = inventory.getItem(toSlot);
        inventory.setItem(fromSlot, toStack);
        inventory.setItem(toSlot, fromStack);
        return true;
    }

    public static boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        if (!(actor.getRawEntity() instanceof Player player)) {
            return false;
        }
        var inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack dropStack = stack.split(Math.min(count, stack.getCount()));
        player.drop(dropStack, false);
        return true;
    }

    @NotNull
    public static List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        List<ILocatedItem> list = new ArrayList<>();
        if (entity.getRawEntity() instanceof Player player) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    list.add(new MinecraftLocatedItem(id, stack.getCount(), i,
                            player.getUUID().toString(), null, null, null, stack));
                }
            }
        }
        return list;
    }
}

