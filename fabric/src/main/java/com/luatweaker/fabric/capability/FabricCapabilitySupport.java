package com.luatweaker.fabric.capability;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Helper methods supporting {@link FabricCapabilityPlatform}. */
final class FabricCapabilitySupport {

    private FabricCapabilitySupport() {}

    @Nullable
    static Direction parseSide(@Nullable String side) {
        if (side == null || side.isBlank()) {
            return null;
        }
        try {
            return Direction.valueOf(side.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static List<Map<String, Object>> collectContainerSlots(Container container) {
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                slots.add(Map.of(
                        "Slot", i,
                        "Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        "Count", stack.getCount()
                ));
            }
        }
        return slots;
    }

    static List<Map<String, Object>> collectStorageSlots(Storage<ItemVariant> storage) {
        List<Map<String, Object>> slots = new ArrayList<>();
        int slotIdx = 0;
        for (StorageView<ItemVariant> view : storage) {
            if (!view.isResourceBlank() && view.getAmount() > 0) {
                slots.add(Map.of(
                        "Slot", slotIdx,
                        "Id", BuiltInRegistries.ITEM.getKey(view.getResource().getItem()).toString(),
                        "Count", (int) Math.min(view.getAmount(), Integer.MAX_VALUE)
                ));
            }
            slotIdx++;
        }
        return slots;
    }

    @Nullable
    static Item resolveItem(String itemId) {
        ResourceLocation rl = itemId.contains(":") ? ResourceLocation.tryParse(itemId) : ResourceLocation.fromNamespaceAndPath("minecraft", itemId);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    static int insertIntoContainer(Container container, Item item, int count) {
        int remaining = count;
        int maxStack = item.getDefaultMaxStackSize();
        ItemStack template = new ItemStack(item);
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack inSlot = container.getItem(i);
            if (inSlot.isEmpty() && container.canPlaceItem(i, template)) {
                int toAdd = Math.min(remaining, maxStack);
                container.setItem(i, template.copyWithCount(toAdd));
                remaining -= toAdd;
            } else if (inSlot.is(item) && inSlot.getCount() < inSlot.getMaxStackSize()) {
                int space = inSlot.getMaxStackSize() - inSlot.getCount();
                int toAdd = Math.min(remaining, space);
                inSlot.grow(toAdd);
                remaining -= toAdd;
            }
        }
        if (remaining < count) {
            container.setChanged();
        }
        return count - remaining;
    }

    static int insertIntoStorage(Storage<ItemVariant> storage, Item item, int count) {
        try (Transaction tx = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(item), count, tx);
            tx.commit();
            return (int) inserted;
        }
    }
}
