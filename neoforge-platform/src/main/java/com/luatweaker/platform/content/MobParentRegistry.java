package com.luatweaker.platform.content;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
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
    public record MobParentAdapter(
            String id,
            EntityFactory factory,
            Supplier<AttributeSupplier.Builder> attributeSupplier
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
                net.minecraft.world.entity.monster.Zombie::createAttributes
        );

        // Creeper
        register("creeper",
                (type, level) -> new net.minecraft.world.entity.monster.Creeper((EntityType) type, level),
                net.minecraft.world.entity.monster.Creeper::createAttributes
        );

        // Skeleton
        register("skeleton",
                (type, level) -> new net.minecraft.world.entity.monster.Skeleton((EntityType) type, level),
                net.minecraft.world.entity.monster.Skeleton::createAttributes
        );

        // Stray
        register("stray",
                (type, level) -> new net.minecraft.world.entity.monster.Stray((EntityType) type, level),
                net.minecraft.world.entity.monster.Skeleton::createAttributes
        );

        // Wither Skeleton
        register("wither_skeleton",
                (type, level) -> new net.minecraft.world.entity.monster.WitherSkeleton((EntityType) type, level),
                net.minecraft.world.entity.monster.WitherSkeleton::createAttributes
        );

        // Bogged
        register("bogged",
                (type, level) -> new net.minecraft.world.entity.monster.Bogged((EntityType) type, level),
                net.minecraft.world.entity.monster.Bogged::createAttributes
        );

        // Iron Golem
        register("iron_golem",
                (type, level) -> new net.minecraft.world.entity.animal.IronGolem((EntityType) type, level),
                net.minecraft.world.entity.animal.IronGolem::createAttributes
        );
        registerAlias("golem", "iron_golem");

        // Snow Golem
        register("snow_golem",
                (type, level) -> new net.minecraft.world.entity.animal.SnowGolem((EntityType) type, level),
                net.minecraft.world.entity.animal.SnowGolem::createAttributes
        );

        // Spider
        register("spider",
                (type, level) -> new net.minecraft.world.entity.monster.Spider((EntityType) type, level),
                net.minecraft.world.entity.monster.Spider::createAttributes
        );

        // Pig
        register("pig",
                (type, level) -> new net.minecraft.world.entity.animal.Pig((EntityType) type, level),
                net.minecraft.world.entity.animal.Pig::createAttributes
        );

        // Cow
        register("cow",
                (type, level) -> new net.minecraft.world.entity.animal.Cow((EntityType) type, level),
                net.minecraft.world.entity.animal.Cow::createAttributes
        );

        // Sheep
        register("sheep",
                (type, level) -> new net.minecraft.world.entity.animal.Sheep((EntityType) type, level),
                net.minecraft.world.entity.animal.Sheep::createAttributes
        );

        // Chicken
        register("chicken",
                (type, level) -> new net.minecraft.world.entity.animal.Chicken((EntityType) type, level),
                net.minecraft.world.entity.animal.Chicken::createAttributes
        );

        // Villager
        register("villager",
                (type, level) -> new net.minecraft.world.entity.npc.Villager((EntityType) type, level),
                net.minecraft.world.entity.npc.Villager::createAttributes
        );

        // Wandering Trader
        register("wandering_trader",
                (type, level) -> new net.minecraft.world.entity.npc.WanderingTrader((EntityType) type, level),
                net.minecraft.world.entity.npc.Villager::createAttributes
        );

        // Slime
        register("slime",
                (type, level) -> new net.minecraft.world.entity.monster.Slime((EntityType) type, level),
                net.minecraft.world.entity.monster.Monster::createMonsterAttributes
        );

        // Witch
        register("witch",
                (type, level) -> new net.minecraft.world.entity.monster.Witch((EntityType) type, level),
                net.minecraft.world.entity.monster.Witch::createAttributes
        );

        // Blaze
        register("blaze",
                (type, level) -> new net.minecraft.world.entity.monster.Blaze((EntityType) type, level),
                net.minecraft.world.entity.monster.Blaze::createAttributes
        );

        // Enderman
        register("enderman",
                (type, level) -> new net.minecraft.world.entity.monster.EnderMan((EntityType) type, level),
                net.minecraft.world.entity.monster.EnderMan::createAttributes
        );

        // Wolf
        register("wolf",
                (type, level) -> new net.minecraft.world.entity.animal.Wolf((EntityType) type, level),
                net.minecraft.world.entity.animal.Wolf::createAttributes
        );

        // Fox
        register("fox",
                (type, level) -> new net.minecraft.world.entity.animal.Fox((EntityType) type, level),
                net.minecraft.world.entity.animal.Fox::createAttributes
        );

        // Cat
        register("cat",
                (type, level) -> new net.minecraft.world.entity.animal.Cat((EntityType) type, level),
                net.minecraft.world.entity.animal.Cat::createAttributes
        );

        // Frog
        register("frog",
                (type, level) -> new net.minecraft.world.entity.animal.frog.Frog((EntityType) type, level),
                net.minecraft.world.entity.animal.frog.Frog::createAttributes
        );

        // Bee
        register("bee",
                (type, level) -> new net.minecraft.world.entity.animal.Bee((EntityType) type, level),
                net.minecraft.world.entity.animal.Bee::createAttributes
        );

        // Goat
        register("goat",
                (type, level) -> new net.minecraft.world.entity.animal.goat.Goat((EntityType) type, level),
                net.minecraft.world.entity.animal.goat.Goat::createAttributes
        );

        // Drowned
        register("drowned",
                (type, level) -> new net.minecraft.world.entity.monster.Drowned((EntityType) type, level),
                net.minecraft.world.entity.monster.Drowned::createAttributes
        );

        // Husk
        register("husk",
                (type, level) -> new net.minecraft.world.entity.monster.Husk((EntityType) type, level),
                net.minecraft.world.entity.monster.Husk::createAttributes
        );

        // Piglin
        register("piglin",
                (type, level) -> new net.minecraft.world.entity.monster.piglin.Piglin((EntityType) type, level),
                net.minecraft.world.entity.monster.piglin.Piglin::createAttributes
        );

        // Zombified Piglin
        register("zombified_piglin",
                (type, level) -> new net.minecraft.world.entity.monster.ZombifiedPiglin((EntityType) type, level),
                net.minecraft.world.entity.monster.ZombifiedPiglin::createAttributes
        );

        // Phantom
        register("phantom",
                (type, level) -> new net.minecraft.world.entity.monster.Phantom((EntityType) type, level),
                net.minecraft.world.entity.monster.Monster::createMonsterAttributes
        );

        // Ghast
        register("ghast",
                (type, level) -> new net.minecraft.world.entity.monster.Ghast((EntityType) type, level),
                net.minecraft.world.entity.monster.Ghast::createAttributes
        );

        // Silverfish
        register("silverfish",
                (type, level) -> new net.minecraft.world.entity.monster.Silverfish((EntityType) type, level),
                net.minecraft.world.entity.monster.Silverfish::createAttributes
        );

        // Warden
        register("warden",
                (type, level) -> new net.minecraft.world.entity.monster.warden.Warden((EntityType) type, level),
                net.minecraft.world.entity.monster.warden.Warden::createAttributes
        );
    }

    public static void register(String id, EntityFactory factory, Supplier<AttributeSupplier.Builder> attributeSupplier) {
        try {
            REGISTRY.put(id.toLowerCase(), new MobParentAdapter(id.toLowerCase(), factory, attributeSupplier));
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
