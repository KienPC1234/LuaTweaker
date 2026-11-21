package com.luatweaker.platform.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;

/** Block-to-block energy/fluid/item transfers behind the Lua {@code World} service. */
final class WorldTransferOps {

    private WorldTransferOps() {
    }

    static int transferEnergy(@NotNull String dimension, int fromX, int fromY, int fromZ,
                              int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0,
                level -> moveEnergy(level, WorldBlockOps.pos(fromX, fromY, fromZ),
                        WorldBlockOps.pos(toX, toY, toZ), maxAmount, simulate));
    }

    private static int moveEnergy(ServerLevel level, BlockPos fromPos, BlockPos toPos,
                                  int maxAmount, boolean simulate) {
        IEnergyStorage fromStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, fromPos, null);
        IEnergyStorage toStorage = level.getCapability(Capabilities.EnergyStorage.BLOCK, toPos, null);
        if (fromStorage == null || toStorage == null) {
            return 0;
        }
        int extracted = fromStorage.extractEnergy(maxAmount, true);
        if (extracted <= 0) {
            return 0;
        }
        int accepted = toStorage.receiveEnergy(extracted, simulate);
        if (accepted > 0 && !simulate) {
            fromStorage.extractEnergy(accepted, false);
        }
        return accepted;
    }

    static int transferFluid(@NotNull String dimension, int fromX, int fromY, int fromZ,
                             int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0,
                level -> moveFluid(level, WorldBlockOps.pos(fromX, fromY, fromZ),
                        WorldBlockOps.pos(toX, toY, toZ), maxAmount, simulate));
    }

    private static int moveFluid(ServerLevel level, BlockPos fromPos, BlockPos toPos,
                                 int maxAmount, boolean simulate) {
        IFluidHandler fromHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, fromPos, null);
        IFluidHandler toHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, toPos, null);
        if (fromHandler == null || toHandler == null) {
            return 0;
        }
        FluidStack drained = fromHandler.drain(maxAmount, IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) {
            return 0;
        }
        int filled = toHandler.fill(drained, simulate
                ? IFluidHandler.FluidAction.SIMULATE
                : IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0 && !simulate) {
            fromHandler.drain(filled, IFluidHandler.FluidAction.EXECUTE);
        }
        return filled;
    }

    /** One item transfer request (a value object because the PAL signature is 11 parameters). */
    record Transfer(int fromX, int fromY, int fromZ, int fromSlot,
                    int toX, int toY, int toZ, int toSlot, int maxCount, boolean simulate) {
    }

    static int transferItem(@NotNull String dimension, @NotNull Transfer request) {
        if (request.maxCount() <= 0) {
            return 0;
        }
        return ServerLevels.map(ServerLevels.resolve(dimension), 0,
                level -> moveItem(level, WorldBlockOps.pos(request.fromX(), request.fromY(), request.fromZ()),
                        request.fromSlot(), WorldBlockOps.pos(request.toX(), request.toY(), request.toZ()),
                        request.toSlot(), request.maxCount(), request.simulate()));
    }

    private static int moveItem(ServerLevel level, BlockPos fromPos, int fromSlot, BlockPos toPos, int toSlot,
                                int maxCount, boolean simulate) {
        IItemHandler fromHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, fromPos, null);
        IItemHandler toHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, toPos, null);
        if (fromHandler == null || toHandler == null || fromSlot < 0 || fromSlot >= fromHandler.getSlots()) {
            return 0;
        }
        ItemStack simulatedExtract = fromHandler.extractItem(fromSlot, maxCount, true);
        if (simulatedExtract.isEmpty()) {
            return 0;
        }
        ItemStack remaining = insertInto(toHandler, simulatedExtract, toSlot, simulate);
        int moved = simulatedExtract.getCount() - remaining.getCount();
        if (moved > 0 && !simulate) {
            fromHandler.extractItem(fromSlot, moved, false);
        }
        return moved;
    }

    private static ItemStack insertInto(IItemHandler handler, ItemStack stack, int slot, boolean simulate) {
        return slot >= 0 && slot < handler.getSlots()
                ? handler.insertItem(slot, stack, simulate)
                : ItemHandlerHelper.insertItem(handler, stack, simulate);
    }
}
