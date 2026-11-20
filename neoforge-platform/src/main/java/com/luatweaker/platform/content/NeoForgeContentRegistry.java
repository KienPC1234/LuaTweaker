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
        this.itemRegistrar = new ItemRegistrar(contentService, createdItems, NeoForgeContentRegistry::parseLocation);
        this.blockRegistrar = new BlockRegistrar(contentService, createdBlocks, createdItems, NeoForgeContentRegistry::parseLocation);
        this.fluidRegistrar = new FluidRegistrar(contentService, createdFluids, createdItems, createdBlocks, NeoForgeContentRegistry::parseLocation);
        this.entityRegistrar = new EntityRegistrar(contentService, createdEntityTypes, createdItems, NeoForgeContentRegistry::parseLocation);
        this.tabRegistrar = new TabRegistrar(contentService, createdItems, createdCustomTabs, NeoForgeContentRegistry::parseLocation);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new GameBusListener());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                new EntityHookDispatcher(contentService, NeoForgeContentRegistry::parseLocation));
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

        @SubscribeEvent
        public void onLeftClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
            ItemRegistrar.handleSwing(event.getEntity(), event.getItemStack());
        }

        @SubscribeEvent
        public void onLeftClickEmpty(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
            ItemRegistrar.handleSwing(event.getEntity(), event.getItemStack());
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

        @SubscribeEvent
        public void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                for (IItemBuilder b : parent.contentService.getRegisteredItems()) {
                    String anim = b.getUseAnimation();
                    if ("BOW".equalsIgnoreCase(anim) || "CROSSBOW".equalsIgnoreCase(anim)) {
                        ResourceLocation rl = NeoForgeContentRegistry.parseLocation(b.getId());
                        Item item = parent.createdItems.get(rl);
                        if (item != null) {
                            net.minecraft.client.renderer.item.ItemProperties.register(
                                    item,
                                    ResourceLocation.withDefaultNamespace("pulling"),
                                    (stack, level, entity, seed) ->
                                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
                            );
                            net.minecraft.client.renderer.item.ItemProperties.register(
                                    item,
                                    ResourceLocation.withDefaultNamespace("pull"),
                                    (stack, level, entity, seed) -> {
                                        if (entity == null || entity.getUseItem() != stack) return 0.0F;
                                        int dur = b.getUseDuration() > 0 ? b.getUseDuration() : 20;
                                        return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) dur;
                                    }
                            );
                            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered bow pulling item properties for " + rl);
                        }
                    }
                }
            });
        }



        @SuppressWarnings("unchecked")
        @SubscribeEvent
        public void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            com.luatweaker.platform.client.LuaMobRenderers.registerLayerDefinitions(event);
        }

        @SubscribeEvent
        public void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            for (com.luatweaker.api.content.IEntityBuilder e : parent.contentService.getRegisteredEntities()) {
                try {
                    ResourceLocation rl = NeoForgeContentRegistry.parseLocation(e.getId());
                    EntityType<?> rawType = parent.createdEntityTypes.get(rl);
                    if (rawType != null) {
                        if (e.getGeoModel() != null) {
                            EntityType entityType = rawType;
                            com.luatweaker.api.content.GeoModelSpec geoSpec = e.getGeoModel();
                            event.registerEntityRenderer(entityType, ctx ->
                                    new com.luatweaker.platform.gecko.LuaGeoEntityRenderer(ctx, geoSpec));
                            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered GeckoLib EntityRenderer for: " + rl);
                            continue;
                        }
                        String rig = e.getRig() == null ? "parent" : e.getRig().strip().toLowerCase();
                        if ("humanoid".equals(rig) || "quadruped".equals(rig)) {
                            registerGenericRenderer(event, e, rl, rawType, rig);
                            continue;
                        }
                        ResourceLocation texLoc = e.getTexture() != null
                                ? NeoForgeContentRegistry.parseLocation(e.getTexture())
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

            for (com.luatweaker.api.content.IBlockBuilder b : parent.contentService.getRegisteredBlocks()) {
                if (b.getGeoModel() != null) {
                    try {
                        ResourceLocation rl = NeoForgeContentRegistry.parseLocation(b.getId());
                        net.minecraft.world.level.block.entity.BlockEntityType<?> rawType =
                                com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_BE_TYPES.get(rl);
                        if (rawType != null) {
                            net.minecraft.world.level.block.entity.BlockEntityType type = rawType;
                            com.luatweaker.api.content.GeoModelSpec geoSpec = b.getGeoModel();
                            event.registerBlockEntityRenderer(type, ctx ->
                                    new com.luatweaker.platform.gecko.LuaGeoBlockRenderer(geoSpec));
                            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered GeckoLib BlockEntityRenderer for: " + rl);
                        }
                    } catch (Exception ex) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed GeckoLib block entity renderer registration for " + b.getId() + ": " + ex.getMessage());
                    }
                } else if (b.getRenderHandler() != null) {
                    try {
                        ResourceLocation rl = NeoForgeContentRegistry.parseLocation(b.getId());
                        net.minecraft.world.level.block.entity.BlockEntityType<?> rawType =
                                com.luatweaker.platform.container.CustomContainerRegistry.CONTAINER_BE_TYPES.get(rl);
                        if (rawType != null) {
                            net.minecraft.world.level.block.entity.BlockEntityType type = rawType;
                            // Resolve the handler per frame: /lt reload replaces it on the block.
                            event.registerBlockEntityRenderer(type, ctx ->
                                    new com.luatweaker.platform.client.LuaBlockEntityRenderer(type, ctx));
                            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockEntityRenderer for: " + rl);
                        }
                    } catch (Exception ex) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed block entity renderer registration for " + b.getId() + ": " + ex.getMessage());
                    }
                }
            }
        }

        /**
         * Texture-driven generic renderer for a humanoid/quadruped-rig mob.
         * A missing texture cannot render correctly, so it warns loudly and
         * falls back to the zombie texture instead of crashing the client.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private void registerGenericRenderer(EntityRenderersEvent.RegisterRenderers event,
                                             com.luatweaker.api.content.IEntityBuilder builder,
                                             ResourceLocation rl, EntityType<?> rawType, String rig) {
            ResourceLocation texLoc;
            if (builder.getTexture() != null && !builder.getTexture().isBlank()) {
                texLoc = NeoForgeContentRegistry.parseLocation(builder.getTexture());
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Custom entity '" + builder.getId() + "' uses rig '" + rig + "' without a texture: "
                                + "falling back to the zombie texture. Set texture(path) on the builder.");
                texLoc = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");
            }
            float shadow = Math.max(0.2f, Math.min(2.0f, builder.getWidth() * 0.5f));
            double renderScale = builder.getRenderScale();
            EntityType type = rawType;
            if ("quadruped".equals(rig)) {
                event.registerEntityRenderer(type,
                        ctx -> com.luatweaker.platform.client.LuaMobRenderers.createQuadruped(
                                ctx, texLoc, shadow, renderScale));
            } else {
                event.registerEntityRenderer(type,
                        ctx -> com.luatweaker.platform.client.LuaMobRenderers.createHumanoid(
                                ctx, texLoc, shadow, renderScale));
            }
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Registered Generic EntityRenderer (Rig: " + rig + ") for: " + rl);
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
                ResourceLocation bucketRl = NeoForgeContentRegistry.parseLocation(builder.getId() + "_bucket");
                Item bucketItem = parent.createdItems.get(bucketRl);
                if (bucketItem != null) {
                    event.register(new net.neoforged.neoforge.client.model.DynamicFluidContainerModel.Colors(), bucketItem);
                }
                ResourceLocation altBucketRl = NeoForgeContentRegistry.parseLocation(builder.getId().replace("_fluid", "") + "_bucket");
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
    public void onRegisterSpawnPlacements(net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent event) {
        entityRegistrar.onRegisterSpawnPlacements(event);
    }

    public static ResourceLocation parseLocation(String id) {
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
