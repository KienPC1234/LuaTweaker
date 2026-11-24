package com.luatweaker.fabric;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.container.CustomContainerBlockEntity;
import com.luatweaker.common.container.CustomContainerRegistry;
import com.luatweaker.fabric.capability.FabricCapabilityPlatform;
import com.luatweaker.fabric.capability.FabricFluidStorage;
import com.luatweaker.fabric.recipe.FabricRecipeAndLootHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

public class LuaTweakerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        LuaTweakerCommon.init();
        Platform.setCapability(new FabricCapabilityPlatform());

        for (BlockEntityType<?> type : CustomContainerRegistry.CONTAINER_BE_TYPES.values()) {
            ItemStorage.SIDED.registerForBlockEntity(LuaTweakerFabric::provideItemStorage, type);
            FluidStorage.SIDED.registerForBlockEntity(LuaTweakerFabric::provideFluidStorage, type);
        }

        FabricRecipeAndLootHandler.register();
    }

    @Nullable
    private static InventoryStorage provideItemStorage(BlockEntity be, @Nullable Direction direction) {
        return be instanceof Container container && container.getContainerSize() > 0
                ? InventoryStorage.of(container, direction)
                : null;
    }

    @Nullable
    private static FabricFluidStorage provideFluidStorage(BlockEntity be, @Nullable Direction direction) {
        boolean validSide = direction == null || direction.getAxis() != null;
        return validSide && be instanceof CustomContainerBlockEntity custom && custom.getFluidCapacity() > 0
                ? new FabricFluidStorage(custom)
                : null;
    }
}
