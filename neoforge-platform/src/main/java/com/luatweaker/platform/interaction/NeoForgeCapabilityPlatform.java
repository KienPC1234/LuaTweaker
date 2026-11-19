package com.luatweaker.platform.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return null;
        }
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (storage == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Stored", storage.getEnergyStored());
        data.put("Max", storage.getMaxEnergyStored());
        data.put("CanExtract", storage.canExtract());
        data.put("CanReceive", storage.canReceive());
        return data;
    }

    @Override
    public int insertEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return 0;
        }
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        return storage == null ? 0 : storage.receiveEnergy(amount, false);
    }

    @Override
    public int extractEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return 0;
        }
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        return storage == null ? 0 : storage.extractEnergy(amount, false);
    }

    @Override
    public @Nullable List<Map<String, Object>> getItemSlots(@NotNull String dimension, int x, int y, int z,
                                                            @Nullable String side) {
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return null;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (handler == null) {
            return null;
        }
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("Slot", slot);
            entry.put("Id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            entry.put("Count", stack.getCount());
            slots.add(entry);
        }
        return slots;
    }

    @Override
    public int insertItem(@NotNull String dimension, int x, int y, int z,
                          @NotNull String itemId, int count, @Nullable String side) {
        if (count <= 0) {
            return 0;
        }
        Item item = parseItem(itemId);
        if (item == null) {
            return 0;
        }
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return 0;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (handler == null) {
            return 0;
        }
        ItemStack remaining = new ItemStack(item, count);
        int requested = remaining.getCount();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return requested - remaining.getCount();
    }

    @Override
    public @Nullable Map<String, Object> extractItem(@NotNull String dimension, int x, int y, int z,
                                                     int slot, int count, @Nullable String side) {
        if (slot < 0 || count <= 0) {
            return null;
        }
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return null;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (handler == null || slot >= handler.getSlots()) {
            return null;
        }
        ItemStack taken = handler.extractItem(slot, count, false);
        if (taken.isEmpty()) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Slot", slot);
        data.put("Id", BuiltInRegistries.ITEM.getKey(taken.getItem()).toString());
        data.put("Count", taken.getCount());
        return data;
    }

    @Override
    public @Nullable Map<String, Object> getFluid(@NotNull String dimension, int x, int y, int z,
                                                  @Nullable String side) {
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return null;
        }
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (handler == null || handler.getTanks() <= 0) {
            return null;
        }
        FluidStack stack = handler.getFluidInTank(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Id", stack.isEmpty()
                ? ""
                : BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
        data.put("Amount", stack.getAmount());
        data.put("Capacity", handler.getTankCapacity(0));
        return data;
    }

    @Override
    public int fillFluid(@NotNull String dimension, int x, int y, int z,
                         @NotNull String fluidId, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        net.minecraft.world.level.material.Fluid fluid = parseFluid(fluidId);
        if (fluid == null) {
            return 0;
        }
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return 0;
        }
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (handler == null) {
            return 0;
        }
        return handler.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public int drainFluid(@NotNull String dimension, int x, int y, int z,
                          @Nullable String fluidId, int amount, @Nullable String side) {
        if (amount <= 0) {
            return 0;
        }
        boolean anyFluid = fluidId == null || fluidId.isBlank() || "*".equals(fluidId.trim());
        net.minecraft.world.level.material.Fluid wanted = anyFluid ? null : parseFluid(fluidId);
        if (!anyFluid && wanted == null) {
            return 0;
        }
        ServerLevel level = ServerLevels.resolve(dimension);
        if (level == null) {
            return 0;
        }
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (handler == null) {
            return 0;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack current = handler.getFluidInTank(tank);
            if (current.isEmpty()) {
                continue;
            }
            if (wanted != null && !current.getFluid().isSame(wanted)) {
                continue;
            }
            FluidStack drained = handler.drain(new FluidStack(current.getFluid(), amount),
                    IFluidHandler.FluidAction.EXECUTE);
            return drained.getAmount();
        }
        return 0;
    }

    private static @Nullable Direction parseSide(@Nullable String side) {
        if (side == null || side.isBlank()) {
            return null;
        }
        try {
            return Direction.valueOf(side.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Capability] unknown side '" + side + "'; using the unsided lookup");
            return null;
        }
    }

    private static @Nullable Item parseItem(@NotNull String itemId) {
        ResourceLocation id = parseId(itemId, "item");
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Capability] unknown item '" + itemId + "'");
        }
        return item;
    }

    private static @Nullable net.minecraft.world.level.material.Fluid parseFluid(@Nullable String fluidId) {
        ResourceLocation id = parseId(fluidId, "fluid");
        if (id == null) {
            return null;
        }
        net.minecraft.world.level.material.Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Capability] unknown fluid '" + fluidId + "'");
        }
        return fluid;
    }

    private static @Nullable ResourceLocation parseId(@Nullable String raw, @NotNull String kind) {
        if (raw == null || raw.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Capability] empty " + kind + " id");
            return null;
        }
        String trimmed = raw.trim();
        ResourceLocation id = trimmed.contains(":")
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.tryParse("minecraft:" + trimmed);
        if (id == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Capability] malformed " + kind + " id '" + raw + "'");
        }
        return id;
    }
}
