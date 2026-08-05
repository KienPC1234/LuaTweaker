package com.luatweaker.platform.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.content.registrar.*;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NeoForgeContentRegistry {
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

    public NeoForgeContentRegistry(IContentService contentService) {
        this.contentService = contentService;
        this.itemRegistrar = new ItemRegistrar(contentService, createdItems, this::parseLocation);
        this.blockRegistrar = new BlockRegistrar(contentService, createdBlocks, createdItems, this::parseLocation);
        this.fluidRegistrar = new FluidRegistrar(contentService, createdFluids, createdItems, createdBlocks, this::parseLocation);
        this.entityRegistrar = new EntityRegistrar(contentService, createdEntityTypes, createdItems, this::parseLocation);
        this.tabRegistrar = new TabRegistrar(contentService, createdItems, createdCustomTabs, this::parseLocation);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new GameBusListener());
    }

    public static class BossBarTickHandler {
        private final EntityRegistrar.BossBarTickHandler delegate;

        public BossBarTickHandler(IContentService contentService) {
            this.delegate = new EntityRegistrar.BossBarTickHandler(contentService, id -> {
                if (id != null && id.contains(":")) return ResourceLocation.parse(id);
                return ResourceLocation.fromNamespaceAndPath("luatweaker", id != null ? id : "unknown");
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

    private class GameBusListener {
        @SubscribeEvent
        public void onProjectileImpact(ProjectileImpactEvent event) {
            if (event.getRayTraceResult() instanceof EntityHitResult hitResult) {
                Projectile proj = event.getProjectile();
                for (IItemBuilder b : contentService.getRegisteredItems()) {
                    String tagCheck = "lt_proj_" + b.getId();
                    if (proj.getTags().contains(tagCheck)) {
                        if (b.getOnHitEntityHandler() != null && proj.getOwner() instanceof Player player) {
                            try {
                                b.getOnHitEntityHandler().accept(
                                        new NeoForgeEntityWrapper(hitResult.getEntity()),
                                        new NeoForgePlayerWrapper(player)
                                );
                            } catch (Exception e) {
                                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed projectile onHitEntity handler for " + b.getId() + ": " + e.getMessage());
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            itemRegistrar.registerItems(event);
            blockRegistrar.registerBlockItems(event);
            fluidRegistrar.registerFluidItems(event);
            entityRegistrar.registerEntitySpawnEggs(event);
        } else if (event.getRegistryKey().equals(Registries.BLOCK)) {
            blockRegistrar.registerBlocks(event);
            fluidRegistrar.registerFluidBlocks(event);
        } else if (event.getRegistryKey().equals(Registries.FLUID)) {
            fluidRegistrar.registerFluids(event);
        } else if (event.getRegistryKey().equals(Registries.ENTITY_TYPE)) {
            entityRegistrar.registerEntityTypes(event);
        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            blockRegistrar.registerContainerBlockEntities(event);
        } else if (event.getRegistryKey().equals(Registries.MENU)) {
            blockRegistrar.registerContainerMenus(event);
        } else if (event.getRegistryKey().equals(NeoForgeRegistries.Keys.FLUID_TYPES)) {
            fluidRegistrar.registerFluidTypes(event);
        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            tabRegistrar.registerCreativeTab(event);
        }
    }

    @SubscribeEvent
    public void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        tabRegistrar.onBuildCreativeModeTabContents(event);
    }

    public static class ClientModEvents {
        private final NeoForgeContentRegistry parent;

        public ClientModEvents(NeoForgeContentRegistry parent) {
            this.parent = parent;
        }



        @SuppressWarnings("unchecked")
        @SubscribeEvent
        public void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            for (com.luatweaker.api.content.IEntityBuilder e : parent.contentService.getRegisteredEntities()) {
                try {
                    ResourceLocation rl = parent.parseLocation(e.getId());
                    EntityType<?> rawType = parent.createdEntityTypes.get(rl);
                    if (rawType != null) {
                        ResourceLocation texLoc = e.getTexture() != null
                                ? parent.parseLocation(e.getTexture())
                                : ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");
                        com.luatweaker.platform.content.ClientMobParentRegistry.RendererFactory factory = com.luatweaker.platform.content.ClientMobParentRegistry.getFactory(e.getParentMob());
                        EntityType entityType = rawType;
                        event.registerEntityRenderer(entityType, ctx -> factory.create(ctx, texLoc));
                        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom EntityRenderer for: " + rl);
                    }
                } catch (Exception ex) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed entity renderer registration for " + e.getId() + ": " + ex.getMessage());
                }
            }
        }

        @SubscribeEvent
        public void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            for (net.minecraft.world.inventory.MenuType<com.luatweaker.platform.container.CustomContainerMenu> menuType
                    : com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_MENUS.values()) {
                event.register(menuType, com.luatweaker.platform.container.CustomContainerScreen::new);
            }
        }

        @SubscribeEvent
        public void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
            for (com.luatweaker.api.content.IFluidBuilder builder : parent.contentService.getRegisteredFluids()) {
                ResourceLocation bucketRl = parent.parseLocation(builder.getId() + "_bucket");
                Item bucketItem = parent.createdItems.get(bucketRl);
                if (bucketItem != null) {
                    event.register(new net.neoforged.neoforge.client.model.DynamicFluidContainerModel.Colors(), bucketItem);
                }
                ResourceLocation altBucketRl = parent.parseLocation(builder.getId().replace("_fluid", "") + "_bucket");
                Item altBucketItem = parent.createdItems.get(altBucketRl);
                if (altBucketItem != null && altBucketItem != bucketItem) {
                    event.register(new net.neoforged.neoforge.client.model.DynamicFluidContainerModel.Colors(), altBucketItem);
                }
            }
        }
    }

    @SubscribeEvent
    public void onRegisterCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        // Expose every Lua-configured container as a standard IItemHandler so external
        // mods (item pipes, automation, scanner tools) can read/move its contents.
        for (net.minecraft.world.level.block.entity.BlockEntityType<com.luatweaker.platform.container.CustomContainerBlockEntity> type
                : com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_BE_TYPES.values()) {
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, type,
                    (be, direction) -> be);
        }
    }

    @SubscribeEvent
    public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        entityRegistrar.onEntityAttributeCreation(event);
    }

    private ResourceLocation parseLocation(String id) {
        if (id == null || id.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("luatweaker", "empty");
        }
        if (id.contains(":")) {
            String[] parts = id.split(":", 2);
            return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
        }
        return ResourceLocation.fromNamespaceAndPath("luatweaker", id);
    }
}
