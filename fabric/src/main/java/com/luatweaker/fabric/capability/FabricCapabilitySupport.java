package com.luatweaker.fabric.capability;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Helper methods supporting {@link FabricCapabilityPlatform}. */
final class FabricCapabilitySupport {

    private FabricCapabilitySupport() {
    }

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
        ResourceLocation rl = itemId.contains(":")
                ? ResourceLocation.tryParse(itemId)
                : ResourceLocation.fromNamespaceAndPath("minecraft", itemId);
        return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
    }

    @Nullable
    static Fluid resolveFluid(@Nullable String fluidId) {
        if (fluidId == null || fluidId.isBlank()) {
            return null;
        }
        String trimmed = fluidId.trim();
        ResourceLocation rl = trimmed.contains(":")
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.fromNamespaceAndPath("minecraft", trimmed);
        if (rl == null) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(rl).orElse(null);
        return fluid == Fluids.EMPTY ? null : fluid;
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

    @Nullable
    static Map<String, Object> readFluidStorage(Storage<FluidVariant> storage) {
        java.util.Iterator<StorageView<FluidVariant>> iterator = storage.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        StorageView<FluidVariant> view = iterator.next();
        String fluidId = view.isResourceBlank()
                ? ""
                : BuiltInRegistries.FLUID.getKey(view.getResource().getFluid()).toString();
        int amountMb = (int) Math.min(view.getAmount() / FabricFluidStorage.DROPLETS_PER_MB, Integer.MAX_VALUE);
        int capacityMb = (int) Math.min(view.getCapacity() / FabricFluidStorage.DROPLETS_PER_MB, Integer.MAX_VALUE);
        return Map.of("Id", fluidId, "Amount", amountMb, "Capacity", capacityMb);
    }

    static int insertFluidIntoStorage(Storage<FluidVariant> storage, Fluid fluid, int amountMb) {
        long droplets = amountMb * FabricFluidStorage.DROPLETS_PER_MB;
        try (Transaction tx = Transaction.openOuter()) {
            long inserted = storage.insert(FluidVariant.of(fluid), droplets, tx);
            tx.commit();
            return (int) (inserted / FabricFluidStorage.DROPLETS_PER_MB);
        }
    }

    static int drainFluidFromStorage(Storage<FluidVariant> storage, @Nullable Fluid wanted, int amountMb) {
        long droplets = amountMb * FabricFluidStorage.DROPLETS_PER_MB;
        for (StorageView<FluidVariant> view : storage) {
            if (view.isResourceBlank() || view.getAmount() <= 0) {
                continue;
            }
            Fluid current = view.getResource().getFluid();
            if (wanted == null || current.isSame(wanted)) {
                try (Transaction tx = Transaction.openOuter()) {
                    long extracted = storage.extract(view.getResource(), droplets, tx);
                    tx.commit();
                    return (int) (extracted / FabricFluidStorage.DROPLETS_PER_MB);
                }
            }
        }
        return 0;
    }
}
