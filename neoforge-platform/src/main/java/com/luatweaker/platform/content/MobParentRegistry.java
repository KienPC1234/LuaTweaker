package com.luatweaker.platform.content;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class MobParentRegistry {
    public interface EntityFactory {
        Mob create(EntityType<?> type, net.minecraft.world.level.Level level);
    }

    public interface RendererFactory {
        EntityRenderer<?> create(EntityRendererProvider.Context ctx, ResourceLocation texture);
    }

    public record MobParentAdapter(
            String id,
            EntityFactory factory,
            Supplier<AttributeSupplier.Builder> attributeSupplier,
            RendererFactory rendererFactory
    ) {}

    private static final Map<String, MobParentAdapter> REGISTRY = new ConcurrentHashMap<>();
    private static final String DEFAULT_PARENT = "zombie";

    static {
        registerDefaults();
    }

    @SuppressWarnings("unchecked")
    private static void registerDefaults() {
        // Zombie (Default Fallback)
        register("zombie",
                (type, level) -> new net.minecraft.world.entity.monster.Zombie((EntityType) type, level),
                net.minecraft.world.entity.monster.Zombie::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.ZombieRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return tex; }
                }
        );

        // Creeper
        register("creeper",
                (type, level) -> new net.minecraft.world.entity.monster.Creeper((EntityType) type, level),
                net.minecraft.world.entity.monster.Creeper::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.CreeperRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Creeper entity) { return tex; }
                }
        );

        // Skeleton
        register("skeleton",
                (type, level) -> new net.minecraft.world.entity.monster.Skeleton((EntityType) type, level),
                net.minecraft.world.entity.monster.Skeleton::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SkeletonRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.AbstractSkeleton entity) { return tex; }
                }
        );

        // Stray
        register("stray",
                (type, level) -> new net.minecraft.world.entity.monster.Stray((EntityType) type, level),
                net.minecraft.world.entity.monster.Skeleton::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SkeletonRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.AbstractSkeleton entity) { return tex; }
                }
        );

        // Wither Skeleton
        register("wither_skeleton",
                (type, level) -> new net.minecraft.world.entity.monster.WitherSkeleton((EntityType) type, level),
                net.minecraft.world.entity.monster.WitherSkeleton::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SkeletonRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.AbstractSkeleton entity) { return tex; }
                }
        );

        // Bogged
        register("bogged",
                (type, level) -> new net.minecraft.world.entity.monster.Bogged((EntityType) type, level),
                net.minecraft.world.entity.monster.Bogged::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SkeletonRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.AbstractSkeleton entity) { return tex; }
                }
        );

        // Iron Golem
        register("iron_golem",
                (type, level) -> new net.minecraft.world.entity.animal.IronGolem((EntityType) type, level),
                net.minecraft.world.entity.animal.IronGolem::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.IronGolemRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.IronGolem entity) { return tex; }
                }
        );
        registerAlias("golem", "iron_golem");

        // Snow Golem
        register("snow_golem",
                (type, level) -> new net.minecraft.world.entity.animal.SnowGolem((EntityType) type, level),
                net.minecraft.world.entity.animal.SnowGolem::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SnowGolemRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.SnowGolem entity) { return tex; }
                }
        );

        // Spider
        register("spider",
                (type, level) -> new net.minecraft.world.entity.monster.Spider((EntityType) type, level),
                net.minecraft.world.entity.monster.Spider::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SpiderRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Spider entity) { return tex; }
                }
        );

        // Pig
        register("pig",
                (type, level) -> new net.minecraft.world.entity.animal.Pig((EntityType) type, level),
                net.minecraft.world.entity.animal.Pig::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.PigRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Pig entity) { return tex; }
                }
        );

        // Cow
        register("cow",
                (type, level) -> new net.minecraft.world.entity.animal.Cow((EntityType) type, level),
                net.minecraft.world.entity.animal.Cow::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.CowRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Cow entity) { return tex; }
                }
        );

        // Sheep
        register("sheep",
                (type, level) -> new net.minecraft.world.entity.animal.Sheep((EntityType) type, level),
                net.minecraft.world.entity.animal.Sheep::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SheepRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Sheep entity) { return tex; }
                }
        );

        // Chicken
        register("chicken",
                (type, level) -> new net.minecraft.world.entity.animal.Chicken((EntityType) type, level),
                net.minecraft.world.entity.animal.Chicken::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.ChickenRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Chicken entity) { return tex; }
                }
        );

        // Villager
        register("villager",
                (type, level) -> new net.minecraft.world.entity.npc.Villager((EntityType) type, level),
                net.minecraft.world.entity.npc.Villager::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.VillagerRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.npc.Villager entity) { return tex; }
                }
        );

        // Wandering Trader
        register("wandering_trader",
                (type, level) -> new net.minecraft.world.entity.npc.WanderingTrader((EntityType) type, level),
                net.minecraft.world.entity.npc.Villager::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.WanderingTraderRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.npc.WanderingTrader entity) { return tex; }
                }
        );

        // Slime
        register("slime",
                (type, level) -> new net.minecraft.world.entity.monster.Slime((EntityType) type, level),
                net.minecraft.world.entity.monster.Monster::createMonsterAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SlimeRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Slime entity) { return tex; }
                }
        );

        // Witch
        register("witch",
                (type, level) -> new net.minecraft.world.entity.monster.Witch((EntityType) type, level),
                net.minecraft.world.entity.monster.Witch::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.WitchRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Witch entity) { return tex; }
                }
        );

        // Blaze
        register("blaze",
                (type, level) -> new net.minecraft.world.entity.monster.Blaze((EntityType) type, level),
                net.minecraft.world.entity.monster.Blaze::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.BlazeRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Blaze entity) { return tex; }
                }
        );

        // Enderman
        register("enderman",
                (type, level) -> new net.minecraft.world.entity.monster.EnderMan((EntityType) type, level),
                net.minecraft.world.entity.monster.EnderMan::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.EndermanRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.EnderMan entity) { return tex; }
                }
        );

        // Wolf
        register("wolf",
                (type, level) -> new net.minecraft.world.entity.animal.Wolf((EntityType) type, level),
                net.minecraft.world.entity.animal.Wolf::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.WolfRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Wolf entity) { return tex; }
                }
        );

        // Fox
        register("fox",
                (type, level) -> new net.minecraft.world.entity.animal.Fox((EntityType) type, level),
                net.minecraft.world.entity.animal.Fox::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.FoxRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Fox entity) { return tex; }
                }
        );

        // Cat
        register("cat",
                (type, level) -> new net.minecraft.world.entity.animal.Cat((EntityType) type, level),
                net.minecraft.world.entity.animal.Cat::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.CatRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Cat entity) { return tex; }
                }
        );

        // Frog
        register("frog",
                (type, level) -> new net.minecraft.world.entity.animal.frog.Frog((EntityType) type, level),
                net.minecraft.world.entity.animal.frog.Frog::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.FrogRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.frog.Frog entity) { return tex; }
                }
        );

        // Bee
        register("bee",
                (type, level) -> new net.minecraft.world.entity.animal.Bee((EntityType) type, level),
                net.minecraft.world.entity.animal.Bee::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.BeeRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Bee entity) { return tex; }
                }
        );

        // Goat
        register("goat",
                (type, level) -> new net.minecraft.world.entity.animal.goat.Goat((EntityType) type, level),
                net.minecraft.world.entity.animal.goat.Goat::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.GoatRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.goat.Goat entity) { return tex; }
                }
        );

        // Drowned
        register("drowned",
                (type, level) -> new net.minecraft.world.entity.monster.Drowned((EntityType) type, level),
                net.minecraft.world.entity.monster.Drowned::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.DrownedRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return tex; }
                }
        );

        // Husk
        register("husk",
                (type, level) -> new net.minecraft.world.entity.monster.Husk((EntityType) type, level),
                net.minecraft.world.entity.monster.Husk::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.HuskRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Zombie entity) { return tex; }
                }
        );

        // Piglin
        register("piglin",
                (type, level) -> new net.minecraft.world.entity.monster.piglin.Piglin((EntityType) type, level),
                net.minecraft.world.entity.monster.piglin.Piglin::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.PiglinRenderer(ctx, net.minecraft.client.model.geom.ModelLayers.PIGLIN, net.minecraft.client.model.geom.ModelLayers.PIGLIN_INNER_ARMOR, net.minecraft.client.model.geom.ModelLayers.PIGLIN_OUTER_ARMOR, false) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.Mob entity) { return tex; }
                }
        );

        // Zombified Piglin
        register("zombified_piglin",
                (type, level) -> new net.minecraft.world.entity.monster.ZombifiedPiglin((EntityType) type, level),
                net.minecraft.world.entity.monster.ZombifiedPiglin::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.PiglinRenderer(ctx, net.minecraft.client.model.geom.ModelLayers.ZOMBIFIED_PIGLIN, net.minecraft.client.model.geom.ModelLayers.ZOMBIFIED_PIGLIN_INNER_ARMOR, net.minecraft.client.model.geom.ModelLayers.ZOMBIFIED_PIGLIN_OUTER_ARMOR, true) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.Mob entity) { return tex; }
                }
        );

        // Phantom
        register("phantom",
                (type, level) -> new net.minecraft.world.entity.monster.Phantom((EntityType) type, level),
                net.minecraft.world.entity.monster.Monster::createMonsterAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.PhantomRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Phantom entity) { return tex; }
                }
        );

        // Ghast
        register("ghast",
                (type, level) -> new net.minecraft.world.entity.monster.Ghast((EntityType) type, level),
                net.minecraft.world.entity.monster.Ghast::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.GhastRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Ghast entity) { return tex; }
                }
        );

        // Silverfish
        register("silverfish",
                (type, level) -> new net.minecraft.world.entity.monster.Silverfish((EntityType) type, level),
                net.minecraft.world.entity.monster.Silverfish::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.SilverfishRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Silverfish entity) { return tex; }
                }
        );

        // Warden
        register("warden",
                (type, level) -> new net.minecraft.world.entity.monster.warden.Warden((EntityType) type, level),
                net.minecraft.world.entity.monster.warden.Warden::createAttributes,
                (ctx, tex) -> new net.minecraft.client.renderer.entity.WardenRenderer(ctx) {
                    @Override public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.warden.Warden entity) { return tex; }
                }
        );
    }

    public static void register(String id, EntityFactory factory, Supplier<AttributeSupplier.Builder> attributeSupplier, RendererFactory rendererFactory) {
        try {
            REGISTRY.put(id.toLowerCase(), new MobParentAdapter(id.toLowerCase(), factory, attributeSupplier, rendererFactory));
        } catch (Throwable t) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Mob adapter [" + id + "] is not supported in this MC version environment: " + t.getMessage());
        }
    }

    public static void registerAlias(String alias, String target) {
        MobParentAdapter adapter = REGISTRY.get(target);
        if (adapter != null) {
            REGISTRY.put(alias.toLowerCase(), adapter);
        }
    }

    public static boolean isSupported(String parentId) {
        if (parentId == null || parentId.isBlank()) return false;
        String clean = parentId.toLowerCase().replace("minecraft:", "");
        return REGISTRY.containsKey(clean);
    }

    public static MobParentAdapter getAdapter(String parentId) {
        if (parentId == null || parentId.isBlank()) {
            return REGISTRY.get(DEFAULT_PARENT);
        }
        String clean = parentId.toLowerCase().replace("minecraft:", "");
        MobParentAdapter adapter = REGISTRY.get(clean);
        if (adapter == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Requested mob parent [" + parentId + "] is NOT supported in current version/environment. Falling back to '" + DEFAULT_PARENT + "'.");
            return REGISTRY.get(DEFAULT_PARENT);
        }
        return adapter;
    }

    public static Set<String> getSupportedMobs() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
