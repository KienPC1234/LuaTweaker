package com.luatweaker.neoforge.capability;

import com.luatweaker.api.pal.IPlatformCapability;
import com.luatweaker.common.interaction.ServerLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NeoForge implementation of the block-capability bridge.
 *
 * <p>Every lookup goes through {@code Level#getCapability}, so it sees exactly what any
 * other mod sees (including per-side providers). Nothing is simulated: insert/extract
 * calls execute immediately on the server thread.</p>
 */
public class NeoForgeCapabilityPlatform implements IPlatformCapability {

    @Override
    public @Nullable Map<String, Object> getEnergy(@NotNull String dimension, int x, int y, int z,
                                                   @Nullable String side) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            if (storage == null) {
                return null;
            }
            return Map.<String, Object>of(
                    "Stored", storage.getEnergyStored(),
                    "Max", storage.getMaxEnergyStored(),
                    "CanExtract", storage.canExtract(),
                    "CanReceive", storage.canReceive());
        });
    }

    @Override
    public int insertEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            return storage == null ? 0 : storage.receiveEnergy(amount, false);
        });
    }

    @Override
    public int extractEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            return storage == null ? 0 : storage.extractEnergy(amount, false);
        });
    }

    @Override
    public @Nullable List<Map<String, Object>> getItemSlots(@NotNull String dimension, int x, int y, int z,
                                                            @Nullable String side) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            if (handler == null) {
                return null;
            }
            List<Map<String, Object>> slots = new ArrayList<>();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    slots.add(Map.of(
                            "Slot", slot,
                            "Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                            "Count", stack.getCount()));
                }
            }
            return slots;
        });
    }

    @Override
    public int insertItem(@NotNull String dimension, int x, int y, int z,
                          @NotNull String itemId, int count, @Nullable String side) {
        Item item = CapabilitySupport.parseItem(itemId);
        if (count <= 0 || item == null) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            if (handler == null) {
                return 0;
            }
            ItemStack remaining = new ItemStack(item, count);
            int requested = remaining.getCount();
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            return requested - remaining.getCount();
        });
    }

    @Override
    public @Nullable Map<String, Object> extractItem(@NotNull String dimension, int x, int y, int z,
                                                     int slot, int count, @Nullable String side) {
        return slot < 0 || count <= 0 ? null : ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            if (handler == null || slot >= handler.getSlots()) {
                return null;
            }
            ItemStack taken = handler.extractItem(slot, count, false);
            if (taken.isEmpty()) {
                return null;
            }
            return Map.<String, Object>of(
                    "Slot", slot,
                    "Id", BuiltInRegistries.ITEM.getKey(taken.getItem()).toString(),
                    "Count", taken.getCount());
        });
    }

    @Override
    public @Nullable Map<String, Object> getFluid(@NotNull String dimension, int x, int y, int z,
                                                  @Nullable String side) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null, level -> {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            if (handler == null || handler.getTanks() <= 0) {
                return null;
            }
            FluidStack stack = handler.getFluidInTank(0);
            String fluidId = stack.isEmpty()
                    ? ""
                    : BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString();
            return Map.<String, Object>of(
                    "Id", fluidId,
                    "Amount", stack.getAmount(),
                    "Capacity", handler.getTankCapacity(0));
        });
    }

    @Override
    public int fillFluid(@NotNull String dimension, int x, int y, int z,
                         @NotNull String fluidId, int amount, @Nullable String side) {
        net.minecraft.world.level.material.Fluid fluid = CapabilitySupport.parseFluid(fluidId);
        if (amount <= 0 || fluid == null) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0, level -> {
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    new BlockPos(x, y, z), CapabilitySupport.parseSide(side));
            return handler == null
                    ? 0
                    : handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
        });
    }

    @Override
    public int drainFluid(@NotNull String dimension, int x, int y, int z,
                          @Nullable String fluidId, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        boolean anyFluid = fluidId == null || fluidId.isBlank() || "*".equals(fluidId.trim());
        net.minecraft.world.level.material.Fluid wanted = anyFluid ? null : CapabilitySupport.parseFluid(fluidId);
        if (!anyFluid && wanted == null) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0,
                level -> CapabilitySupport.drainFirstMatching(level, x, y, z, side, wanted, amount));
    }


}





