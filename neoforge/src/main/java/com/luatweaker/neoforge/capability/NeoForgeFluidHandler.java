package com.luatweaker.neoforge.capability;

import com.luatweaker.common.container.CustomContainerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;

/**
 * NeoForge {@link IFluidHandler} adapter for {@link CustomContainerBlockEntity}.
 */
public class NeoForgeFluidHandler implements IFluidHandler {
    private static final int TANK_COUNT = 1;
    private static final int PRIMARY_TANK = 0;

    private final CustomContainerBlockEntity blockEntity;

    public NeoForgeFluidHandler(@NotNull CustomContainerBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getTanks() {
        return TANK_COUNT;
    }

    @Override
    @NotNull
    public FluidStack getFluidInTank(int tank) {
        if (tank != PRIMARY_TANK || blockEntity.getFluidId().isEmpty() || blockEntity.getFluidAmount() <= 0) {
            return FluidStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(blockEntity.getFluidId());
        if (id == null) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }
        return new FluidStack(fluid, blockEntity.getFluidAmount());
    }

    @Override
    public int getTankCapacity(int tank) {
        return tank == PRIMARY_TANK ? blockEntity.getFluidCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return tank == PRIMARY_TANK && !stack.isEmpty();
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        String id = BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
        return blockEntity.fillFluid(id, resource.getAmount(), action.execute());
    }

    @Override
    @NotNull
    public FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
        if (resource.isEmpty() || blockEntity.getFluidId().isEmpty() || blockEntity.getFluidAmount() <= 0) {
            return FluidStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(blockEntity.getFluidId());
        if (id == null) {
            return FluidStack.EMPTY;
        }
        Fluid currentFluid = BuiltInRegistries.FLUID.get(id);
        if (currentFluid == null || !currentFluid.isSame(resource.getFluid())) {
            return FluidStack.EMPTY;
        }
        int drained = blockEntity.drainFluid(resource.getAmount(), action.execute());
        return drained > 0 ? new FluidStack(currentFluid, drained) : FluidStack.EMPTY;
    }

    @Override
    @NotNull
    public FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        if (maxDrain <= 0 || blockEntity.getFluidId().isEmpty() || blockEntity.getFluidAmount() <= 0) {
            return FluidStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(blockEntity.getFluidId());
        if (id == null) {
            return FluidStack.EMPTY;
        }
        Fluid currentFluid = BuiltInRegistries.FLUID.get(id);
        if (currentFluid == null || currentFluid == Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }
        int drained = blockEntity.drainFluid(maxDrain, action.execute());
        return drained > 0 ? new FluidStack(currentFluid, drained) : FluidStack.EMPTY;
    }
}
