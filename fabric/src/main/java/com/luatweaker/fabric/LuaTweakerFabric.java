package com.luatweaker.fabric;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.container.CustomContainerRegistry;
import com.luatweaker.fabric.capability.FabricCapabilityPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class LuaTweakerFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        LuaTweakerCommon.init();
        Platform.setCapability(new FabricCapabilityPlatform());
        for (BlockEntityType<?> type : CustomContainerRegistry.CONTAINER_BE_TYPES.values()) {
            ItemStorage.SIDED.registerForBlockEntity((be, direction) -> be instanceof net.minecraft.world.Container container
                    ? InventoryStorage.of(container, direction) : null, type);
        }
    }
}
