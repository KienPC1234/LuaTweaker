package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.content.MobParentRegistry;
import com.luatweaker.platform.entity.LuaCustomMob;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EntityRegistrar {
    private static final String UNCHECKED = "unchecked";
    private static final String RIG_PARENT = "parent";
    private static final String RIG_HUMANOID = "humanoid";
    private static final String RIG_QUADRUPED = "quadruped";

    private final IContentService contentService;
    private final Map<ResourceLocation, EntityType<?>> createdEntityTypes;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;

    public EntityRegistrar(IContentService contentService, Map<ResourceLocation, EntityType<?>> createdEntityTypes,
                           Map<ResourceLocation, Item> createdItems,
                           Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdEntityTypes = createdEntityTypes;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
    }

    @SuppressWarnings(UNCHECKED)
    public void registerEntityTypes(RegisterEvent event) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            try {
                registerOne(event, builder);
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Failed to register entity type " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    @SuppressWarnings(UNCHECKED)
    private void registerOne(RegisterEvent event, IEntityBuilder builder) {
        ResourceLocation rl = locationParser.apply(builder.getId());
        MobCategory category = parseCategory(builder.getCategory());
        String rig = builder.getRig() == null ? RIG_PARENT : builder.getRig().strip().toLowerCase(Locale.ROOT);
        EntityType.EntityFactory<Mob> factory = createFactory(builder, rig, rl);
        EntityType.Builder<Mob> typeBuilder = EntityType.Builder.<Mob>of(factory, category)
                .sized(builder.getWidth(), builder.getHeight());
        if (builder.isFireImmune()) {
            typeBuilder.fireImmune();
        }
        EntityType<Mob> type = typeBuilder.build(rl.toString());
        event.register(Registries.ENTITY_TYPE, rl, () -> type);
        createdEntityTypes.put(rl, type);
        if (isGenericRig(rig)) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Registered Custom EntityType (Rig: " + rig + "): " + rl);
        }
    }

    @SuppressWarnings(UNCHECKED)
    private EntityType.EntityFactory<Mob> createFactory(IEntityBuilder builder, String rig, ResourceLocation rl) {
        if (builder.getGeoModel() != null) {
            var geoSpec = builder.getGeoModel();
            storeMobConfig(rl, "geo", builder);
            return (type, level) -> new com.luatweaker.platform.integration.geckolib.LuaGeoMob(type, level, geoSpec);
        }
        if (isGenericRig(rig)) {
            storeMobConfig(rl, rig, builder);
            return LuaCustomMob::createGeneric;
        }
        if (!RIG_PARENT.equals(rig)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Entity '" + builder.getId() + "' has unknown rig '" + builder.getRig()
                            + "': falling back to the legacy 'parent' rig.");
        }
        MobParentRegistry.MobParentAdapter adapter = MobParentRegistry.getAdapter(builder.getParentMob());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered Custom EntityType (Adapter: " + adapter.id() + "): " + rl);
        return adapter.factory()::create;
    }

    private static void storeMobConfig(ResourceLocation rl, String rig, IEntityBuilder builder) {
        com.luatweaker.platform.entity.LuaMobConfigs.put(rl.toString(),
                new com.luatweaker.platform.entity.LuaMobConfigs.Snapshot(
                        rig, resolveDefaultGoals(builder), builder.getAmbientSound(),
                        builder.getHurtSound(), builder.getDeathSound()));
    }

    public void registerEntitySpawnEggs(RegisterEvent event) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            if (!builder.hasSpawnEgg()) {
                continue;
            }
            try {
                registerEgg(event, builder);
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Failed to register entity spawn egg " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    @SuppressWarnings(UNCHECKED)
    private void registerEgg(RegisterEvent event, IEntityBuilder builder) {
        ResourceLocation eggId = locationParser.apply(builder.getId() + "_spawn_egg");
        ResourceLocation entityId = locationParser.apply(builder.getId());
        Supplier<EntityType<? extends Mob>> typeSupplier = () -> (EntityType) createdEntityTypes.get(entityId);
        Item eggItem = new DeferredSpawnEggItem(typeSupplier, builder.getPrimaryColor(),
                builder.getSecondaryColor(), new Item.Properties());
        event.register(Registries.ITEM, eggId, () -> eggItem);
        createdItems.put(eggId, eggItem);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity Spawn Egg Item: " + eggId);
    }

    public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            registerAttributes(event, builder);
        }
    }

    @SuppressWarnings(UNCHECKED)
    private void registerAttributes(EntityAttributeCreationEvent event, IEntityBuilder builder) {
        ResourceLocation rl = locationParser.apply(builder.getId());
        EntityType entityType = createdEntityTypes.get(rl);
        if (entityType == null) {
            return;
        }
        MobParentRegistry.MobParentAdapter adapter = MobParentRegistry.getAdapter(builder.getParentMob());
        var attributes = adapter.attributeSupplier().get();
        attributes.add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, builder.getMaxHealth())
                .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, builder.getMovementSpeed())
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, builder.getAttackDamage())
                .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, builder.getFollowRange())
                .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, builder.getArmor())
                .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
                        builder.getKnockbackResistance());
        event.put(entityType, attributes.build());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered Custom Entity Attributes (Adapter: " + adapter.id() + ") for: " + rl);
    }

    /**
     * Spawn restrictions (docs: worldgen/biomemodifier.md): without a registered
     * spawn placement, custom entities cannot spawn through the vanilla natural
     * spawner. ON_GROUND + the standard mob predicate is used for every custom
     * entity.
     */
    public void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            try {
                registerSpawnPlacement(event, builder);
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Failed to register spawn placement for " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    @SuppressWarnings(UNCHECKED)
    private void registerSpawnPlacement(RegisterSpawnPlacementsEvent event, IEntityBuilder builder) {
        ResourceLocation rl = locationParser.apply(builder.getId());
        EntityType<Mob> entityType = (EntityType<Mob>) createdEntityTypes.get(rl);
        if (entityType == null) {
            return;
        }
        event.register(entityType,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity Spawn Placement: " + rl);
    }

    private static boolean isGenericRig(String rig) {
        return RIG_HUMANOID.equals(rig) || RIG_QUADRUPED.equals(rig);
    }

    private static MobCategory parseCategory(String category) {
        if (category == null) {
            return MobCategory.MONSTER;
        }
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "CREATURE" -> MobCategory.CREATURE;
            case "AMBIENT" -> MobCategory.AMBIENT;
            case "WATER_CREATURE" -> MobCategory.WATER_CREATURE;
            case "MISC" -> MobCategory.MISC;
            default -> MobCategory.MONSTER;
        };
    }

    /**
     * Resolves the AI preset for a generic-rig mob: explicit builder value wins,
     * otherwise MONSTER gets hostile defaults, CREATURE gets animal defaults,
     * everything else starts motionless.
     */
    private static String resolveDefaultGoals(IEntityBuilder builder) {
        if (builder.getDefaultGoals() != null) {
            return builder.getDefaultGoals();
        }
        String category = builder.getCategory() == null ? "" : builder.getCategory().toUpperCase(Locale.ROOT);
        return switch (category) {
            case "MONSTER" -> "monster";
            case "CREATURE", "AMBIENT", "WATER_CREATURE" -> "animal";
            default -> "none";
        };
    }
}
