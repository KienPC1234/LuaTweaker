package com.luatweaker.fabric.capability;

import com.luatweaker.common.container.CustomContainerBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric Transfer API {@link SingleSlotStorage} adapter for {@link CustomContainerBlockEntity}.
 * Converts between mB (1000 per bucket) and Fabric droplets ({@link FluidConstants#BUCKET} = 81000).
 */
public final class FabricFluidStorage extends SnapshotParticipant<FabricFluidStorage.FluidSnapshot>
        implements SingleSlotStorage<FluidVariant> {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000L;

    public record FluidSnapshot(@NotNull String fluidId, int amountMb) {
    }

    private final CustomContainerBlockEntity blockEntity;

    public FabricFluidStorage(@NotNull CustomContainerBlockEntity blockEntity) {
        super();
        this.blockEntity = blockEntity;
    }

    @Override
    public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount < DROPLETS_PER_MB) {
            return 0L;
        }
        int requestedMb = (int) Math.min(maxAmount / DROPLETS_PER_MB, Integer.MAX_VALUE);
        String incomingId = BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString();
        int simulatedMb = blockEntity.fillFluid(incomingId, requestedMb, false);
        if (simulatedMb <= 0) {
            return 0L;
        }
        updateSnapshots(transaction);
        int insertedMb = blockEntity.fillFluid(incomingId, simulatedMb, true);
        return insertedMb * DROPLETS_PER_MB;
    }

    @Override
    public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
        if (resource.isBlank() || maxAmount < DROPLETS_PER_MB) {
            return 0L;
        }
        Fluid current = currentFluid();
        if (current == Fluids.EMPTY || !current.isSame(resource.getFluid())) {
            return 0L;
        }
        int requestedMb = (int) Math.min(maxAmount / DROPLETS_PER_MB, Integer.MAX_VALUE);
        int simulatedMb = blockEntity.drainFluid(requestedMb, false);
        if (simulatedMb <= 0) {
            return 0L;
        }
        updateSnapshots(transaction);
        int extractedMb = blockEntity.drainFluid(simulatedMb, true);
        return extractedMb * DROPLETS_PER_MB;
    }

    @Override
    public boolean isResourceBlank() {
        return getResource().isBlank();
    }

    @Override
    public FluidVariant getResource() {
        Fluid current = currentFluid();
        return current == Fluids.EMPTY || blockEntity.getFluidAmount() <= 0
                ? FluidVariant.blank()
                : FluidVariant.of(current);
    }

    @Override
    public long getAmount() {
        return blockEntity.getFluidAmount() * DROPLETS_PER_MB;
    }

    @Override
    public long getCapacity() {
        return blockEntity.getFluidCapacity() * DROPLETS_PER_MB;
    }

    @Override
    protected FluidSnapshot createSnapshot() {
        return new FluidSnapshot(blockEntity.getFluidId(), blockEntity.getFluidAmount());
    }

    @Override
    protected void readSnapshot(FluidSnapshot snapshot) {
        int existing = blockEntity.getFluidAmount();
        if (existing > 0) {
            blockEntity.drainFluid(existing, true);
        }
        if (!snapshot.fluidId().isEmpty() && snapshot.amountMb() > 0) {
            blockEntity.fillFluid(snapshot.fluidId(), snapshot.amountMb(), true);
        }
    }

    private Fluid currentFluid() {
        String id = blockEntity.getFluidId();
        if (id == null || id.isEmpty()) {
            return Fluids.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return Fluids.EMPTY;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(rl);
        return fluid == null ? Fluids.EMPTY : fluid;
    }
}
