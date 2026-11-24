package com.luatweaker.common.content;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientMobParentRegistry {
    private static final String FALLBACK_RENDERER = "zombie";


    private ClientMobParentRegistry() {
    }
    @FunctionalInterface
    public interface RendererFactory {
        EntityRenderer<?> create(EntityRendererProvider.Context ctx, ResourceLocation texture);
    }

    private static final Map<String, RendererFactory> RENDERERS = new ConcurrentHashMap<>();

    static {
        registerDefaults();
    }

    private static void register(String id, RendererFactory factory) {
        RENDERERS.put(id, factory);
    }

    public static RendererFactory getFactory(String id) {
        if (id == null) { return RENDERERS.get(FALLBACK_RENDERER); }
        String clean = id.toLowerCase(java.util.Locale.ROOT);
        RendererFactory factory = RENDERERS.get(clean);
        if (factory == null && !FALLBACK_RENDERER.equals(clean)) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "No custom renderer for parent '" + id + "': falling back to the zombie renderer "
                            + "(visuals will be wrong). Use rig('humanoid' | 'quadruped') for a texture-driven generic renderer.");
            return RENDERERS.get(FALLBACK_RENDERER);
        }
        return factory != null ? factory : RENDERERS.get(FALLBACK_RENDERER);
    }

    private static void registerDefaults() {
        register(FALLBACK_RENDERER, (ctx, tex) -> new net.minecraft.client.renderer.entity.ZombieRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return tex; }
        });
        register("skeleton", (ctx, tex) -> new net.minecraft.client.renderer.entity.SkeletonRenderer<>(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.AbstractSkeleton entity) { return tex; }
        });
        register("creeper", (ctx, tex) -> new net.minecraft.client.renderer.entity.CreeperRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Creeper entity) { return tex; }
        });
        register("pig", (ctx, tex) -> new net.minecraft.client.renderer.entity.PigRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Pig entity) { return tex; }
        });
        register("cow", (ctx, tex) -> new net.minecraft.client.renderer.entity.CowRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Cow entity) { return tex; }
        });
        RendererFactory ironGolemFactory = (ctx, tex) -> new net.minecraft.client.renderer.entity.IronGolemRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.IronGolem entity) { return tex; }
        };
        register("iron_golem", ironGolemFactory);
        register("golem", ironGolemFactory);
        register("spider", (ctx, tex) -> new net.minecraft.client.renderer.entity.SpiderRenderer<>(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Spider entity) { return tex; }
        });
        register("snow_golem", (ctx, tex) -> new net.minecraft.client.renderer.entity.SnowGolemRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.SnowGolem entity) { return tex; }
        });
        register("slime", (ctx, tex) -> new net.minecraft.client.renderer.entity.SlimeRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Slime entity) { return tex; }
        });
        register("blaze", (ctx, tex) -> new net.minecraft.client.renderer.entity.BlazeRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Blaze entity) { return tex; }
        });
        register("enderman", (ctx, tex) -> new net.minecraft.client.renderer.entity.EndermanRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.EnderMan entity) { return tex; }
        });
        register("chicken", (ctx, tex) -> new net.minecraft.client.renderer.entity.ChickenRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Chicken entity) { return tex; }
        });
        register("sheep", (ctx, tex) -> new net.minecraft.client.renderer.entity.SheepRenderer(ctx) {
            @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Sheep entity) { return tex; }
        });
    }
}
