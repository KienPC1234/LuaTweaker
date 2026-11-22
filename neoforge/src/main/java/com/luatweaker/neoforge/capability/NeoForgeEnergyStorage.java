package com.luatweaker.neoforge.capability;

import com.luatweaker.common.container.CustomContainerBlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;

/**
 * NeoForge {@link IEnergyStorage} adapter for {@link CustomContainerBlockEntity}.
 */
public class NeoForgeEnergyStorage implements IEnergyStorage {
    private final CustomContainerBlockEntity blockEntity;

    public NeoForgeEnergyStorage(@NotNull CustomContainerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return blockEntity.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return blockEntity.extractEnergy(maxExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return blockEntity.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return blockEntity.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return blockEntity.canExtract();
    }

    @Override
    public boolean canReceive() {
        return blockEntity.canReceive();
    }
}
