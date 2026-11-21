package com.luatweaker.platform.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.client.LuaBlockEntityRenderer;
import com.luatweaker.platform.client.LuaMobRenderers;
import com.luatweaker.platform.container.CustomContainerRegistry;
import com.luatweaker.platform.integration.geckolib.LuaGeoBlockRenderer;
import com.luatweaker.platform.integration.geckolib.LuaGeoEntityRenderer;
import com.luatweaker.platform.util.CallbackFirewall;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.Locale;

/** Client renderer registration for Lua entities and block entities. */
final class ContentRendererRegistrar {
    private static final String RIG_PARENT = "parent";
    private static final String RIG_HUMANOID = "humanoid";
    private static final String RIG_QUADRUPED = "quadruped";
    private static final String FALLBACK_ENTITY_TEXTURE = "textures/entity/zombie/zombie.png";
    private static final float MIN_SHADOW = 0.2f;
    private static final float MAX_SHADOW = 2.0f;
    private static final float SHADOW_PER_BLOCK_WIDTH = 0.5f;

    private ContentRendererRegistrar() {
    }

    static void register(NeoForgeContentRegistry registry, EntityRenderersEvent.RegisterRenderers event) {
        for (IEntityBuilder builder : registry.getContentService().getRegisteredEntities()) {
            CallbackFirewall.run(() -> registerEntityRenderer(registry, builder, event), LogStage.SYSTEM,
                    () -> "Failed entity renderer registration for " + builder.getId());
        }
        for (IBlockBuilder builder : registry.getContentService().getRegisteredBlocks()) {
            CallbackFirewall.run(() -> registerBlockRenderer(builder, event), LogStage.SYSTEM,
                    () -> "Failed block entity renderer registration for " + builder.getId());
        }
    }

    private static void registerEntityRenderer(NeoForgeContentRegistry registry, IEntityBuilder builder,
                                               EntityRenderersEvent.RegisterRenderers event) {
        ResourceLocation id = NeoForgeContentRegistry.parseLocation(builder.getId());
        EntityType<?> rawType = registry.getCreatedEntityTypes().get(id);
        if (rawType == null) {
            return;
        }
        if (builder.getGeoModel() != null) {
            registerGeoEntity(builder, event, id, rawType);
            return;
        }
        String rig = builder.getRig() == null ? RIG_PARENT : builder.getRig().strip().toLowerCase(Locale.ROOT);
        if (isGenericRig(rig)) {
            registerGeneric(builder, event, id, rawType, rig);
            return;
        }
        registerParentRenderer(builder, event, id, rawType);
    }

    private static void registerParentRenderer(IEntityBuilder builder, EntityRenderersEvent.RegisterRenderers event,
                                               ResourceLocation id, EntityType<?> rawType) {
        ResourceLocation texture = builder.getTexture() != null
                ? NeoForgeContentRegistry.parseLocation(builder.getTexture())
                : ResourceLocation.fromNamespaceAndPath(ResourceLocation.DEFAULT_NAMESPACE, FALLBACK_ENTITY_TEXTURE);
        ClientMobParentRegistry.RendererFactory factory = ClientMobParentRegistry.getFactory(builder.getParentMob());
        EntityType type = rawType;
        event.registerEntityRenderer(type, ctx -> factory.create(ctx, texture));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom EntityRenderer for: " + id);
    }

    private static void registerGeoEntity(IEntityBuilder builder, EntityRenderersEvent.RegisterRenderers event,
                                          ResourceLocation id, EntityType<?> rawType) {
        var geoSpec = builder.getGeoModel();
        EntityType type = rawType;
        event.registerEntityRenderer(type, ctx -> new LuaGeoEntityRenderer(ctx, geoSpec));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered GeckoLib EntityRenderer for: " + id);
    }

    /**
     * Texture-driven generic renderer for a humanoid/quadruped-rig mob.
     * A missing texture cannot render correctly, so it warns loudly and
     * falls back to the zombie texture instead of crashing the client.
     */
    private static void registerGeneric(IEntityBuilder builder, EntityRenderersEvent.RegisterRenderers event,
                                        ResourceLocation id, EntityType<?> rawType, String rig) {
        ResourceLocation texture = builder.getTexture() != null && !builder.getTexture().isBlank()
                ? NeoForgeContentRegistry.parseLocation(builder.getTexture())
                : fallbackTexture(builder, rig);
        float shadow = Math.max(MIN_SHADOW, Math.min(MAX_SHADOW, builder.getWidth() * SHADOW_PER_BLOCK_WIDTH));
        double renderScale = builder.getRenderScale();
        EntityType type = rawType;
        if (RIG_QUADRUPED.equals(rig)) {
            event.registerEntityRenderer(type,
                    ctx -> LuaMobRenderers.createQuadruped(ctx, texture, shadow, renderScale));
        } else {
            event.registerEntityRenderer(type,
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

    private static void registerBlockRenderer(IBlockBuilder builder,
                                              EntityRenderersEvent.RegisterRenderers event) {
        ResourceLocation id = NeoForgeContentRegistry.parseLocation(builder.getId());
        BlockEntityType<?> rawType = CustomContainerRegistry.CONTAINER_BE_TYPES.get(id);
        if (rawType == null) {
            return;
        }
        BlockEntityType type = rawType;
        if (builder.getGeoModel() != null) {
            var geoSpec = builder.getGeoModel();
            event.registerBlockEntityRenderer(type, ctx -> new LuaGeoBlockRenderer(geoSpec));
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered GeckoLib BlockEntityRenderer for: " + id);
        } else if (builder.getRenderHandler() != null) {
            // Resolve the handler per frame: /lt reload replaces it on the block.
            event.registerBlockEntityRenderer(type, ctx -> new LuaBlockEntityRenderer(type, ctx));
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockEntityRenderer for: " + id);
        }
    }

    private static boolean isGenericRig(String rig) {
        return RIG_HUMANOID.equals(rig) || RIG_QUADRUPED.equals(rig);
    }
}


