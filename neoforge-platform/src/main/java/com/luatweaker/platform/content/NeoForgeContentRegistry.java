package com.luatweaker.platform.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.platform.content.registrar.BlockRegistrar;
import com.luatweaker.platform.content.registrar.EntityBossBarHandler;
import com.luatweaker.platform.content.registrar.EntityHookDispatcher;
import com.luatweaker.platform.content.registrar.EntityLootDispatcher;
import com.luatweaker.platform.content.registrar.EntityRegistrar;
import com.luatweaker.platform.content.registrar.FluidRegistrar;
import com.luatweaker.platform.content.registrar.ItemRegistrar;
import com.luatweaker.platform.content.registrar.MobEffectRegistrar;
import com.luatweaker.platform.content.registrar.TabRegistrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers every Lua-declared item/block/fluid/entity/tab and exposes the created
 * registries to the client and game bus hooks.
 */
public class NeoForgeContentRegistry {
    private static final String DEFAULT_NAMESPACE = "luatweaker";

    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Block> createdBlocks = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Fluid> createdFluids = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, EntityType<?>> createdEntityTypes = new ConcurrentHashMap<>();
    private final Set<ResourceLocation> createdCustomTabs = ConcurrentHashMap.newKeySet();

    private final ItemRegistrar itemRegistrar;
    private final BlockRegistrar blockRegistrar;
    private final FluidRegistrar fluidRegistrar;
    private final EntityRegistrar entityRegistrar;
    private final TabRegistrar tabRegistrar;
    private final MobEffectRegistrar mobEffectRegistrar = new MobEffectRegistrar();
    private final Map<net.minecraft.resources.ResourceKey<?>,
            java.util.function.Consumer<RegisterEvent>> registerHandlers =
            new ConcurrentHashMap<>();

    public NeoForgeContentRegistry(IContentService contentService) {
        this.contentService = contentService;
        this.itemRegistrar = new ItemRegistrar(contentService, createdItems, NeoForgeContentRegistry::parseLocation);
        this.blockRegistrar = new BlockRegistrar(contentService, createdBlocks, createdItems,
                NeoForgeContentRegistry::parseLocation);
        this.fluidRegistrar = new FluidRegistrar(contentService, createdFluids, createdItems, createdBlocks,
                NeoForgeContentRegistry::parseLocation);
        this.entityRegistrar = new EntityRegistrar(contentService, createdEntityTypes, createdItems,
                NeoForgeContentRegistry::parseLocation);
        this.tabRegistrar = new TabRegistrar(contentService, createdItems, createdCustomTabs,
                NeoForgeContentRegistry::parseLocation);

        registerHandlers.put(Registries.ITEM, event -> {
            itemRegistrar.registerItems(event);
            blockRegistrar.registerBlockItems(event);
            fluidRegistrar.registerFluidItems(event);
            entityRegistrar.registerEntitySpawnEggs(event);
        });
        registerHandlers.put(Registries.BLOCK, event -> {
            blockRegistrar.registerBlocks(event);
            fluidRegistrar.registerFluidBlocks(event);
        });
        registerHandlers.put(Registries.FLUID, fluidRegistrar::registerFluids);
        registerHandlers.put(Registries.ENTITY_TYPE, entityRegistrar::registerEntityTypes);
        registerHandlers.put(Registries.BLOCK_ENTITY_TYPE,
                blockRegistrar::registerContainerBlockEntities);
        registerHandlers.put(Registries.MENU, blockRegistrar::registerContainerMenus);
        registerHandlers.put(NeoForgeRegistries.Keys.FLUID_TYPES,
                fluidRegistrar::registerFluidTypes);
        registerHandlers.put(Registries.CREATIVE_MODE_TAB, tabRegistrar::registerCreativeTab);
        registerHandlers.put(Registries.MOB_EFFECT, mobEffectRegistrar::registerMobEffects);
        registerHandlers.put(Registries.POTION, mobEffectRegistrar::registerPotions);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new ContentGameBusListener(contentService));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                new EntityHookDispatcher(contentService, NeoForgeContentRegistry::parseLocation));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                new EntityLootDispatcher(contentService, NeoForgeContentRegistry::parseLocation));
    }

    IContentService getContentService() {
        return contentService;
    }

    Map<ResourceLocation, Item> getCreatedItems() {
        return createdItems;
    }

    Map<ResourceLocation, EntityType<?>> getCreatedEntityTypes() {
        return createdEntityTypes;
    }

    @SubscribeEvent
    public void onRegister(RegisterEvent event) {
        java.util.function.Consumer<RegisterEvent> handler =
                registerHandlers.get(event.getRegistryKey());
        if (handler != null) {
            handler.accept(event);
        }
    }

    @SubscribeEvent
    public void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        tabRegistrar.onBuildCreativeModeTabContents(event);
    }

    @SubscribeEvent
    public void onRegisterCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        // Expose every Lua-configured container as a standard IItemHandler so external
        // mods (item pipes, automation, scanner tools) can read/move its contents.
        for (var type : com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_BE_TYPES.values()) {
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, type,
                    (be, direction) -> be);
            // Machine blocks (Lua:EnergyStorage / Lua:FluidStorage) additionally expose
            // the NeoForge energy and fluid capabilities - cables/ducts can charge and
            // fill them without any extra mods.
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, type,
                    (be, direction) -> be.getEnergyCapacity() > 0 ? be : null);
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, type,
                    (be, direction) -> be.getFluidCapacity() > 0 ? be : null);
        }
    }

    @SubscribeEvent
    public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        entityRegistrar.onEntityAttributeCreation(event);
    }

    @SubscribeEvent
    public void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        entityRegistrar.onRegisterSpawnPlacements(event);
    }

    public static ResourceLocation parseLocation(String id) {
        if (id == null || id.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, "empty");
        }
        if (id.contains(":")) {
            String[] parts = id.split(":", 2);
            return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
        }
        return ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, id);
    }

    /** Mod-bus client hooks (registered by {@code LuaTweakerMod}); delegates to {@link ContentClientEvents}. */
    public static class ClientModEvents {
        private final ContentClientEvents delegate;

        public ClientModEvents(NeoForgeContentRegistry parent) {
            this.delegate = new ContentClientEvents(parent);
        }

        @SubscribeEvent
        public void onClientSetup(FMLClientSetupEvent event) {
            delegate.onClientSetup(event);
        }

        @SubscribeEvent
        public void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            delegate.onRegisterLayerDefinitions(event);
        }

        @SubscribeEvent
        public void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            delegate.onRegisterRenderers(event);
        }

        @SubscribeEvent
        public void onRegisterScreens(RegisterMenuScreensEvent event) {
            delegate.onRegisterScreens(event);
        }

        @SubscribeEvent
        public void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
            delegate.onRegisterItemColors(event);
        }
    }

    /** Boss-bar tracker kept as a nested type because {@code LuaTweakerMod} registers it by name. */
    public static class BossBarTickHandler {
        private final EntityBossBarHandler delegate;

        public BossBarTickHandler(IContentService contentService) {
            this.delegate = new EntityBossBarHandler(contentService, id -> {
                if (id != null && id.contains(":")) {
                    return ResourceLocation.parse(id);
                }
                return ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, id != null ? id : "unknown");
            });
        }

        @SubscribeEvent
        public void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
            delegate.onLivingDeath(event);
        }

        @SubscribeEvent
        public void onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
            delegate.onEntityTick(event);
        }
    }
}
