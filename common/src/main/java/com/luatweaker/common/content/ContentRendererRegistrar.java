package com.luatweaker.common.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.client.LuaBlockEntityRenderer;
import com.luatweaker.common.client.LuaMobRenderers;
import com.luatweaker.common.container.CustomContainerRegistry;
import com.luatweaker.common.util.CallbackFirewall;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Locale;
import java.util.Map;

/** Client renderer registration for Lua entities and block entities. */
public final class ContentRendererRegistrar {
    private static final String RIG_PARENT = "parent";
    private static final String RIG_HUMANOID = "humanoid";
    private static final String RIG_QUADRUPED = "quadruped";
    private static final String FALLBACK_ENTITY_TEXTURE = "textures/entity/zombie/zombie.png";
    private static final float MIN_SHADOW = 0.2f;
    private static final float MAX_SHADOW = 2.0f;
    private static final float SHADOW_PER_BLOCK_WIDTH = 0.5f;

    private ContentRendererRegistrar() {
    }

    private static final String UNCHECKED = "unchecked";
    private static final String RAWTYPES = "rawtypes";

    @SuppressWarnings({UNCHECKED, RAWTYPES})
    public static void register(IContentService contentService, Map<ResourceLocation, EntityType<?>> createdEntityTypes) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            CallbackFirewall.run(() -> registerEntityRenderer(builder, createdEntityTypes), LogStage.SYSTEM,
                    () -> "Failed entity renderer registration for " + builder.getId());
        }
        registerPendingBlockRenderers(contentService);
    }

    @SuppressWarnings({UNCHECKED, RAWTYPES})
    private static void registerEntityRenderer(IEntityBuilder builder, Map<ResourceLocation, EntityType<?>> createdEntityTypes) {
        ResourceLocation id = ResourceLocation.tryParse(builder.getId());
        if (id == null) {
            return;
        }
        EntityType<?> rawType = createdEntityTypes.get(id);
        if (rawType == null) {
            return;
        }
        if (builder.getGeoModel() != null) {
            var geoSpec = builder.getGeoModel();
            EntityRendererRegistry.register(() -> (EntityType) rawType, ctx -> new com.luatweaker.common.integration.geckolib.LuaGeoEntityRenderer(ctx, geoSpec));
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered GeckoLib EntityRenderer for: " + id);
            return;
        }
        String rig = builder.getRig() == null ? RIG_PARENT : builder.getRig().strip().toLowerCase(Locale.ROOT);
        if (isGenericRig(rig)) {
            registerGeneric(builder, id, (EntityType<Mob>) rawType, rig);
            return;
        }
        registerParentRenderer(builder, id, rawType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerParentRenderer(IEntityBuilder builder, ResourceLocation id, EntityType<?> rawType) {
        ResourceLocation texture = builder.getTexture() != null
                ? ResourceLocation.tryParse(builder.getTexture())
                : ResourceLocation.fromNamespaceAndPath(ResourceLocation.DEFAULT_NAMESPACE, FALLBACK_ENTITY_TEXTURE);
        ClientMobParentRegistry.RendererFactory factory = ClientMobParentRegistry.getFactory(builder.getParentMob());
        EntityRendererRegistry.register(() -> (EntityType) rawType, ctx -> factory.create(ctx, texture));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom EntityRenderer for: " + id);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerGeneric(IEntityBuilder builder, ResourceLocation id, EntityType<Mob> rawType, String rig) {
        ResourceLocation texture = builder.getTexture() != null && !builder.getTexture().isBlank()
                ? ResourceLocation.tryParse(builder.getTexture())
                : fallbackTexture(builder, rig);
        float shadow = Math.max(MIN_SHADOW, Math.min(MAX_SHADOW, builder.getWidth() * SHADOW_PER_BLOCK_WIDTH));
        double renderScale = builder.getRenderScale();
        EntityType<com.luatweaker.common.entity.LuaCustomMob> customType = (EntityType<com.luatweaker.common.entity.LuaCustomMob>) (Object) rawType;
        if (RIG_QUADRUPED.equals(rig)) {
            EntityRendererRegistry.register(() -> customType,
                    ctx -> LuaMobRenderers.createQuadruped(ctx, texture, shadow, renderScale));
        } else {
            EntityRendererRegistry.register(() -> customType,
                    ctx -> LuaMobRenderers.createHumanoid(ctx, texture, shadow, renderScale));
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered Generic EntityRenderer (Rig: " + rig + ") for: " + id);
    }

    private static ResourceLocation fallbackTexture(IEntityBuilder builder, String rig) {
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "Custom entity '" + builder.getId() + "' uses rig '" + rig + "' without a texture: "
                        + "falling back to the zombie texture. Set texture(path) on the builder.");
        return ResourceLocation.fromNamespaceAndPath(ResourceLocation.DEFAULT_NAMESPACE, FALLBACK_ENTITY_TEXTURE);
    }

    private static final java.util.Set<ResourceLocation> REGISTERED_BLOCK_RENDERERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @SuppressWarnings({UNCHECKED, RAWTYPES})
    public static void registerBlockRenderer(IBlockBuilder builder, BlockEntityType<?> type) {
        ResourceLocation id = ResourceLocation.tryParse(builder.getId());
        if (id == null || type == null || !REGISTERED_BLOCK_RENDERERS.add(id)) {
            return;
        }
        BlockEntityType rawType = type;
        if (builder.getGeoModel() != null) {
            var geoSpec = builder.getGeoModel();
            BlockEntityRendererRegistry.register(rawType, ctx -> new com.luatweaker.common.integration.geckolib.LuaGeoBlockRenderer(geoSpec));
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered GeckoLib BlockEntityRenderer for: " + id);
        } else if (builder.getRenderHandler() != null) {
            BlockEntityRendererRegistry.register(rawType, ctx -> new LuaBlockEntityRenderer(rawType, ctx));
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockEntityRenderer for: " + id);
        }
    }

    public static void registerPendingBlockRenderers(IContentService contentService) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            ResourceLocation id = ResourceLocation.tryParse(builder.getId());
            if (id != null) {
                BlockEntityType<?> type = CustomContainerRegistry.CONTAINER_BE_TYPES.get(id);
                if (type != null) {
                    CallbackFirewall.run(() -> registerBlockRenderer(builder, type), LogStage.SYSTEM,
                            () -> "Failed block entity renderer registration for " + builder.getId());
                }
            }
        }
    }

    private static boolean isGenericRig(String rig) {
        return RIG_HUMANOID.equals(rig) || RIG_QUADRUPED.equals(rig);
    }
}
