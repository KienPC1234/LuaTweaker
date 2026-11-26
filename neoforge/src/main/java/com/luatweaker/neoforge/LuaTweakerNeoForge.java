package com.luatweaker.neoforge;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.bootstrap.LuaRuntimeReloader;
import com.luatweaker.common.container.CustomContainerBlockEntity;
import com.luatweaker.common.container.CustomContainerRegistry;
import com.luatweaker.common.datapack.DatapackMaterializer;
import com.luatweaker.neoforge.bootstrap.FmlLoadingScreenProgress;
import com.luatweaker.neoforge.capability.NeoForgeCapabilityPlatform;
import com.luatweaker.neoforge.capability.NeoForgeEnergyStorage;
import com.luatweaker.neoforge.capability.NeoForgeFluidHandler;
import com.luatweaker.neoforge.client.NeoForgeClientExtensions;
import com.luatweaker.neoforge.content.ClientPackFinder;
import com.luatweaker.neoforge.content.registrar.FluidBucketAssets;
import com.luatweaker.neoforge.event.NeoForgeGameEvents;
import com.luatweaker.neoforge.loot.NeoForgeRuntimeLootHandler;
import com.luatweaker.neoforge.recipe.NeoForgeRecipeHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import java.io.File;

@Mod("luatweaker")
public class LuaTweakerNeoForge {

    public LuaTweakerNeoForge(IEventBus modBus) {
        LuaRuntimeReloader.setLoadObserver(new FmlLoadingScreenProgress());
        DatapackMaterializer.addRebuildHook(FluidBucketAssets::inject);
        LuaTweakerCommon.init();
        Platform.setCapability(new NeoForgeCapabilityPlatform());

        modBus.addListener(this::registerCapabilities);

        IContentService contentService = LuaTweakerCommon.getContentService();
        IDatapackService datapackService = LuaTweakerCommon.getDatapackService();
        if (contentService != null && datapackService != null) {
            FluidBucketAssets.inject(datapackService, contentService);
            File luaDir = new File(dev.architectury.platform.Platform.getGameFolder().toFile(), "luamods");
            new ClientPackFinder(luaDir, datapackService).register(modBus);
        }

        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
            NeoForgeClientExtensions.register(modBus);
            NeoForgeGameEvents.register();
        }

        NeoForgeRecipeHandler.register();
        NeoForgeRuntimeLootHandler.register();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var type : CustomContainerRegistry.CONTAINER_BE_TYPES.values()) {
            registerOneBlockEntityCapabilities(event, type);
        }
    }

    private void registerOneBlockEntityCapabilities(RegisterCapabilitiesEvent event,
                                                    BlockEntityType<CustomContainerBlockEntity> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, LuaTweakerNeoForge::resolveItemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, direction) -> resolveEnergyStorage(be));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, direction) -> resolveFluidHandler(be));
    }

    private static IItemHandler resolveItemHandler(CustomContainerBlockEntity be, Direction direction) {
        return be.getContainerSize() > 0 ? new SidedInvWrapper(be, direction) : null;
    }

    private static IEnergyStorage resolveEnergyStorage(CustomContainerBlockEntity be) {
        return be.getEnergyCapacity() > 0 ? new NeoForgeEnergyStorage(be) : null;
    }

    private static IFluidHandler resolveFluidHandler(CustomContainerBlockEntity be) {
        return be.getFluidCapacity() > 0 ? new NeoForgeFluidHandler(be) : null;
    }
}
