package com.luatweaker.fabric.capability;

import com.luatweaker.api.pal.IPlatformCapability;
import com.luatweaker.common.container.CustomContainerBlockEntity;
import com.luatweaker.common.interaction.ServerLevels;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Fabric implementation of {@link IPlatformCapability} backed by
 * {@link CustomContainerBlockEntity} energy/fluid tanks, Fabric Transfer API
 * ({@link ItemStorage#SIDED}, {@link FluidStorage#SIDED}), and vanilla {@link Container}.
 */
public class FabricCapabilityPlatform implements IPlatformCapability {

    @Override
    public @Nullable Map<String, Object> getEnergy(@NotNull String dimension, int x, int y, int z,
                                                   @Nullable String side) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
            if (be instanceof CustomContainerBlockEntity custom && custom.getEnergyCapacity() > 0) {
                return Map.<String, Object>of(
                        "Stored", custom.getEnergyStored(),
                        "Max", custom.getMaxEnergyStored(),
                        "CanExtract", custom.canExtract(),
                        "CanReceive", custom.canReceive());
            }
            return null;
        });
    }

    @Override
    public int insertEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
            if (be instanceof CustomContainerBlockEntity custom && custom.getEnergyCapacity() > 0) {
                return custom.receiveEnergy(amount, false);
            }
            return 0;
        });
    }

    @Override
    public int extractEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
            if (be instanceof CustomContainerBlockEntity custom && custom.getEnergyCapacity() > 0) {
                return custom.extractEnergy(amount, false);
            }
            return 0;
        });
    }

    @Override
    public @Nullable List<Map<String, Object>> getItemSlots(@NotNull String dimension, int x, int y, int z,
                                                            @Nullable String side) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            BlockPos pos = new BlockPos(x, y, z);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                return FabricCapabilitySupport.collectContainerSlots(container);
            }
            Direction dir = FabricCapabilitySupport.parseSide(side);
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, dir);
            return storage == null ? null : FabricCapabilitySupport.collectStorageSlots(storage);
        });
    }

    @Override
    public int insertItem(@NotNull String dimension, int x, int y, int z,
                          @NotNull String itemId, int count, @Nullable String side) {
        if (count <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            BlockPos pos = new BlockPos(x, y, z);
            Item item = FabricCapabilitySupport.resolveItem(itemId);
            if (item == null) {
                return 0;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                return FabricCapabilitySupport.insertIntoContainer(container, item, count);
            }
            Direction dir = FabricCapabilitySupport.parseSide(side);
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, dir);
            return storage == null ? 0 : FabricCapabilitySupport.insertIntoStorage(storage, item, count);
        });
    }

    @Override
    public @Nullable Map<String, Object> extractItem(@NotNull String dimension, int x, int y, int z,
                                                     int slot, int count, @Nullable String side) {
        if (count <= 0) {
            return null;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            BlockPos pos = new BlockPos(x, y, z);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                if (slot < 0 || slot >= container.getContainerSize()) {
                    return null;
                }
                ItemStack inSlot = container.getItem(slot);
                if (inSlot.isEmpty()) {
                    return null;
                }
                int toTake = Math.min(count, inSlot.getCount());
                ItemStack removed = container.removeItem(slot, toTake);
                container.setChanged();
                return Map.of(
                        "Slot", slot,
                        "Id", BuiltInRegistries.ITEM.getKey(removed.getItem()).toString(),
                        "Count", removed.getCount()
                );
            }
            return null;
        });
    }

    @Override
    public @Nullable Map<String, Object> getFluid(@NotNull String dimension, int x, int y, int z,
                                                  @Nullable String side) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            BlockPos pos = new BlockPos(x, y, z);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CustomContainerBlockEntity custom && custom.getFluidCapacity() > 0) {
                return Map.<String, Object>of(
                        "Id", custom.getFluidId(),
                        "Amount", custom.getFluidAmount(),
                        "Capacity", custom.getFluidCapacity());
            }
            Direction dir = FabricCapabilitySupport.parseSide(side);
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, dir);
            return storage == null ? null : FabricCapabilitySupport.readFluidStorage(storage);
        });
    }

    @Override
    public int fillFluid(@NotNull String dimension, int x, int y, int z,
                         @NotNull String fluidId, int amount, @Nullable String side) {
        Fluid fluid = FabricCapabilitySupport.resolveFluid(fluidId);
        if (amount <= 0 || fluid == null) {
            return 0;
        }
        String canonicalFluidId = BuiltInRegistries.FLUID.getKey(fluid).toString();
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            BlockPos pos = new BlockPos(x, y, z);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CustomContainerBlockEntity custom && custom.getFluidCapacity() > 0) {
                return custom.fillFluid(canonicalFluidId, amount, true);
            }
            Direction dir = FabricCapabilitySupport.parseSide(side);
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, dir);
            return storage == null ? 0 : FabricCapabilitySupport.insertFluidIntoStorage(storage, fluid, amount);
        });
    }

    @Override
    public int drainFluid(@NotNull String dimension, int x, int y, int z,
                          @Nullable String fluidId, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        boolean anyFluid = fluidId == null || fluidId.isBlank() || "*".equals(fluidId.trim());
        Fluid wanted = anyFluid ? null : FabricCapabilitySupport.resolveFluid(fluidId);
        if (!anyFluid && wanted == null) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            BlockPos pos = new BlockPos(x, y, z);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CustomContainerBlockEntity custom && custom.getFluidCapacity() > 0) {
                return drainCustomFluid(custom, wanted, amount);
            }
            Direction dir = FabricCapabilitySupport.parseSide(side);
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, dir);
            return storage == null ? 0 : FabricCapabilitySupport.drainFluidFromStorage(storage, wanted, amount);
        });
    }

    private static int drainCustomFluid(CustomContainerBlockEntity custom, @Nullable Fluid wanted, int amount) {
        String currentId = custom.getFluidId();
        if (currentId.isEmpty()) {
            return 0;
        }
        if (wanted != null && !currentId.equals(BuiltInRegistries.FLUID.getKey(wanted).toString())) {
            return 0;
        }
        return custom.drainFluid(amount, true);
    }
}
