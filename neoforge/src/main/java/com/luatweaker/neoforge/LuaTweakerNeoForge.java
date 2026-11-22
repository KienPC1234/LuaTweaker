package com.luatweaker.neoforge;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.container.CustomContainerBlockEntity;
import com.luatweaker.common.container.CustomContainerRegistry;
import com.luatweaker.neoforge.capability.NeoForgeCapabilityPlatform;
import com.luatweaker.neoforge.content.ClientPackFinder;
import com.luatweaker.neoforge.content.registrar.FluidBucketAssets;
import com.luatweaker.neoforge.content.registrar.FluidBucketColors;
import com.luatweaker.neoforge.content.registrar.FluidRegistrar;
import com.luatweaker.neoforge.loot.NeoForgeRuntimeLootHandler;
import com.luatweaker.neoforge.recipe.NeoForgeRecipeHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod("luatweaker")
public class LuaTweakerNeoForge {

    private final Map<ResourceLocation, Fluid> createdFluids = new ConcurrentHashMap<>();
    private FluidRegistrar fluidRegistrar;

    public LuaTweakerNeoForge(IEventBus modBus) {
        LuaTweakerCommon.init();
        Platform.setCapability(new NeoForgeCapabilityPlatform());

        IContentService contentService = LuaTweakerCommon.getContentService();
        IDatapackService datapackService = LuaTweakerCommon.getDatapackService();
        if (contentService != null) {
            this.fluidRegistrar = new FluidRegistrar(
                    contentService,
                    createdFluids,
                    LuaTweakerCommon.getCreatedItems(),
                    LuaTweakerCommon.getCreatedBlocks(),
                    LuaTweakerCommon::parseLocation
            );
        }

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::onRegisterEvent);
        modBus.addListener(this::onRegisterItemColors);

        if (contentService != null && datapackService != null) {
            FluidBucketAssets.inject(datapackService, contentService);
            java.io.File luaDir = new java.io.File(
                    dev.architectury.platform.Platform.getGameFolder().toFile(), "luamods");
            new ClientPackFinder(luaDir, datapackService).register(modBus);
        }

        if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
            com.luatweaker.neoforge.client.NeoForgeClientExtensions.register(modBus);
        }

        NeoForgeRecipeHandler.register();
        NeoForgeRuntimeLootHandler.register();
        com.luatweaker.neoforge.event.NeoForgeGameEvents.register();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var type : CustomContainerRegistry.CONTAINER_BE_TYPES.values()) {
            registerOneBlockEntityCapabilities(event, type);
        }
    }

    private void registerOneBlockEntityCapabilities(
            RegisterCapabilitiesEvent event,
            net.minecraft.world.level.block.entity.BlockEntityType<CustomContainerBlockEntity> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, LuaTweakerNeoForge::resolveItemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, direction) -> resolveEnergyStorage(be));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, direction) -> resolveFluidHandler(be));
    }

    private static net.neoforged.neoforge.items.IItemHandler resolveItemHandler(
            CustomContainerBlockEntity be, net.minecraft.core.Direction direction) {
        return be.getContainerSize() > 0 ? new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(be, direction) : null;
    }

    private static net.neoforged.neoforge.energy.IEnergyStorage resolveEnergyStorage(CustomContainerBlockEntity be) {
        return be.getEnergyCapacity() > 0 ? new com.luatweaker.neoforge.capability.NeoForgeEnergyStorage(be) : null;
    }

    private static net.neoforged.neoforge.fluids.capability.IFluidHandler resolveFluidHandler(CustomContainerBlockEntity be) {
        return be.getFluidCapacity() > 0 ? new com.luatweaker.neoforge.capability.NeoForgeFluidHandler(be) : null;
    }

    private void onRegisterEvent(RegisterEvent event) {
        if (fluidRegistrar == null) {
            return;
        }
        if (event.getRegistryKey().equals(Registries.FLUID)) {
            fluidRegistrar.registerFluids(event);
        } else if (event.getRegistryKey().equals(Registries.BLOCK)) {
            fluidRegistrar.registerFluidBlocks(event);
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            fluidRegistrar.registerFluidItems(event);
        } else if (event.getRegistryKey().equals(NeoForgeRegistries.Keys.FLUID_TYPES)) {
            fluidRegistrar.registerFluidTypes(event);
        }
    }

    private void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        IContentService contentService = LuaTweakerCommon.getContentService();
        if (contentService != null) {
            FluidBucketColors.register(
                    contentService,
                    LuaTweakerCommon.getCreatedItems(),
                    LuaTweakerCommon::parseLocation,
                    event
            );
        }
    }
}
