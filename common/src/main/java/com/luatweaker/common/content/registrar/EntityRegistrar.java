package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.content.MobParentRegistry;
import com.luatweaker.common.entity.LuaCustomMob;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.level.entity.SpawnPlacementsRegistry;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

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
    public void registerEntities() {
        Registrar<EntityType<?>> entityRegistrar = RegistrarManager.get("luatweaker").get(Registries.ENTITY_TYPE);
        Registrar<Item> itemRegistrar = RegistrarManager.get("luatweaker").get(Registries.ITEM);

        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            try {
                registerOne(entityRegistrar, itemRegistrar, builder);
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Failed to register entity type " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    @SuppressWarnings(UNCHECKED)
    private void registerOne(Registrar<EntityType<?>> entityRegistrar, Registrar<Item> itemRegistrar, IEntityBuilder builder) {
        ResourceLocation rl = locationParser.apply(builder.getId());
        MobCategory category = parseCategory(builder.getCategory());
        String rig = builder.getRig() == null ? RIG_PARENT : builder.getRig().strip().toLowerCase(Locale.ROOT);
        EntityType.EntityFactory<Mob> factory = createFactory(builder, rig, rl);
        EntityType.Builder<Mob> typeBuilder = EntityType.Builder.<Mob>of(factory, category)
                .sized(builder.getWidth(), builder.getHeight());
        if (builder.isFireImmune()) {
            typeBuilder.fireImmune();
        }
        // EntityType allocation touches the (already frozen at mod-construction
        // time) entity registry, so the build itself must run inside the
        // deferred registration supplier. The holder below resolves once the
        // entity RegisterEvent fires - strictly before item/color/tab/client
        // events that consume it, mirroring the old direct RegisterEvent order.
        RegistrySupplier<EntityType<?>> typeHolder = entityRegistrar.register(rl, () -> {
            EntityType<Mob> built = typeBuilder.build(rl.toString());
            createdEntityTypes.put(rl, built);
            return built;
        });

        if (isGenericRig(rig)) {
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom EntityType (Rig: " + rig + "): " + rl);
        }

        registerAttributes(() -> (EntityType<Mob>) typeHolder.get(), builder);
        registerSpawnEgg(itemRegistrar, builder, typeHolder);

        // Register Spawn Placement
        SpawnPlacementsRegistry.register(() -> (EntityType<Mob>) typeHolder.get(),
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                Mob::checkMobSpawnRules);
    }

    private void registerAttributes(java.util.function.Supplier<EntityType<Mob>> type, IEntityBuilder builder) {
        MobParentRegistry.MobParentAdapter adapter = MobParentRegistry.getAdapter(builder.getParentMob());
        EntityAttributeRegistry.register(type::get, () -> {
            var attributes = adapter.attributeSupplier().get();
            if (builder.hasExplicitMaxHealth()) {
                attributes.add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, builder.getMaxHealth());
            }
            if (builder.hasExplicitMovementSpeed()) {
                attributes.add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, builder.getMovementSpeed());
            }
            if (builder.hasExplicitAttackDamage()) {
                attributes.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, builder.getAttackDamage());
            }
            if (builder.hasExplicitFollowRange()) {
                attributes.add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, builder.getFollowRange());
            }
            if (builder.hasExplicitArmor()) {
                attributes.add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, builder.getArmor());
            }
            if (builder.hasExplicitKnockbackResistance()) {
                attributes.add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, builder.getKnockbackResistance());
            }
            return attributes;
        });
    }

    @SuppressWarnings(UNCHECKED)
    private void registerSpawnEgg(Registrar<Item> itemRegistrar, IEntityBuilder builder,
                                  RegistrySupplier<EntityType<?>> typeHolder) {
        if (builder.hasSpawnEgg()) {
            ResourceLocation eggId = locationParser.apply(builder.getId() + "_spawn_egg");
            itemRegistrar.register(eggId, () -> {
                EntityType<Mob> resolved = (EntityType<Mob>) typeHolder.get();
                Item eggItem = new SpawnEggItem(resolved, builder.getPrimaryColor(), builder.getSecondaryColor(),
                        new Item.Properties());
                createdItems.put(eggId, eggItem);
                return eggItem;
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity Spawn Egg Item: " + eggId);
        }
    }

    @SuppressWarnings(UNCHECKED)
    private EntityType.EntityFactory<Mob> createFactory(IEntityBuilder builder, String rig, ResourceLocation rl) {
        if (builder.getGeoModel() != null) {
            var geoSpec = builder.getGeoModel();
            storeMobConfig(rl, "geo", builder);
            return (type, level) -> new com.luatweaker.common.integration.geckolib.LuaGeoMob(type, level, geoSpec);
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
        com.luatweaker.common.entity.LuaMobConfigs.put(rl.toString(),
                new com.luatweaker.common.entity.LuaMobConfigs.Snapshot(
                        rig, resolveDefaultGoals(builder), builder.getAmbientSound(),
                        builder.getHurtSound(), builder.getDeathSound()));
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
            case "MONSTER" -> MobCategory.MONSTER;
            default -> throw new IllegalArgumentException("Unknown entity category '" + category
                    + "' (use 'CREATURE', 'MONSTER', 'AMBIENT', 'WATER_CREATURE' or 'MISC')");
        };
    }

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
