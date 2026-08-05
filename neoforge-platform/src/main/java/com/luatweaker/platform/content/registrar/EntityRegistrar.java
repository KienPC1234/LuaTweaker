package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.content.MobParentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EntityRegistrar {
    private final IContentService contentService;
    private final Map<ResourceLocation, EntityType<?>> createdEntityTypes;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;

    public EntityRegistrar(IContentService contentService, Map<ResourceLocation, EntityType<?>> createdEntityTypes, Map<ResourceLocation, Item> createdItems, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdEntityTypes = createdEntityTypes;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
    }

    @SuppressWarnings("unchecked")
    public void registerEntityTypes(RegisterEvent event) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            try {
                ResourceLocation rl = locationParser.apply(builder.getId());
                MobCategory category = parseCategory(builder.getCategory());
                MobParentRegistry.MobParentAdapter adapter = MobParentRegistry.getAdapter(builder.getParentMob());
                EntityType.EntityFactory factory = adapter.factory()::create;

                EntityType<Mob> type = EntityType.Builder.<Mob>of(factory, category)
                        .sized(builder.getWidth(), builder.getHeight()).build(rl.toString());

                event.register(Registries.ENTITY_TYPE, rl, () -> type);
                createdEntityTypes.put(rl, type);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom EntityType (Adapter: " + adapter.id() + "): " + rl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register entity type " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public void registerEntitySpawnEggs(RegisterEvent event) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            if (builder.hasSpawnEgg()) {
                try {
                    ResourceLocation rl = locationParser.apply(builder.getId() + "_spawn_egg");
                    ResourceLocation entityRl = locationParser.apply(builder.getId());
                    @SuppressWarnings("unchecked")
                    Supplier<EntityType<? extends Mob>> typeSupplier =
                            () -> (EntityType) createdEntityTypes.get(entityRl);

                    Item eggItem = new DeferredSpawnEggItem(
                            typeSupplier,
                            builder.getPrimaryColor(),
                            builder.getSecondaryColor(),
                            new Item.Properties()
                    );
                    event.register(Registries.ITEM, rl, () -> eggItem);
                    createdItems.put(rl, eggItem);
                    LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity Spawn Egg Item: " + rl);
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register entity spawn egg " + builder.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            ResourceLocation rl = locationParser.apply(builder.getId());
            @SuppressWarnings("unchecked")
            EntityType entityType = createdEntityTypes.get(rl);
            if (entityType != null) {
                MobParentRegistry.MobParentAdapter adapter = MobParentRegistry.getAdapter(builder.getParentMob());
                AttributeSupplier.Builder attrBuilder = adapter.attributeSupplier().get();

                attrBuilder.add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, builder.getMaxHealth())
                           .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, builder.getMovementSpeed())
                           .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, builder.getAttackDamage())
                           .add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, builder.getFollowRange())
                           .add(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, builder.getArmor())
                           .add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, builder.getKnockbackResistance());

                event.put(entityType, attrBuilder.build());
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity Attributes (Adapter: " + adapter.id() + ") for: " + rl);
            }
        }
    }



    private MobCategory parseCategory(String cat) {
        if (cat == null) return MobCategory.MONSTER;
        return switch (cat.toUpperCase()) {
            case "CREATURE" -> MobCategory.CREATURE;
            case "AMBIENT" -> MobCategory.AMBIENT;
            case "WATER_CREATURE" -> MobCategory.WATER_CREATURE;
            case "MISC" -> MobCategory.MISC;
            default -> MobCategory.MONSTER;
        };
    }

    public static class BossBarTickHandler {
        private final IContentService contentService;
        private final Map<UUID, ServerBossEvent> activeBossBars = new ConcurrentHashMap<>();
        private final Function<String, ResourceLocation> locationParser;

        public BossBarTickHandler(IContentService contentService, Function<String, ResourceLocation> locationParser) {
            this.contentService = contentService;
            this.locationParser = locationParser;
        }

        public void onLivingDeath(LivingDeathEvent event) {
            if (event.getEntity() != null) {
                ServerBossEvent bossEvent = activeBossBars.remove(event.getEntity().getUUID());
                if (bossEvent != null) {
                    bossEvent.removeAllPlayers();
                    bossEvent.setVisible(false);
                }
            }
        }

        public void onEntityTick(EntityTickEvent.Post event) {
            Entity rawEntity = event.getEntity();
            if (rawEntity instanceof LivingEntity entity && !entity.level().isClientSide()) {
                UUID uuid = entity.getUUID();
                if (!entity.isAlive() || entity.isRemoved()) {
                    ServerBossEvent bossEvent = activeBossBars.remove(uuid);
                    if (bossEvent != null) {
                        bossEvent.removeAllPlayers();
                        bossEvent.setVisible(false);
                    }
                    return;
                }

                ResourceLocation entityRl = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                if (entityRl != null) {
                    for (IEntityBuilder b : contentService.getRegisteredEntities()) {
                        if (b.hasBossBar() && locationParser.apply(b.getId()).equals(entityRl)) {
                            ServerBossEvent bossEvent = activeBossBars.computeIfAbsent(uuid, k -> {
                                Component name = Component.literal(b.getBossBarTitle() != null ? b.getBossBarTitle() : b.getId());
                                BossEvent.BossBarColor color = parseBossColor(b.getBossBarColor());
                                BossEvent.BossBarOverlay overlay = parseBossOverlay(b.getBossBarOverlay());
                                return new ServerBossEvent(name, color, overlay);
                            });

                            bossEvent.setProgress(Math.max(0.0f, Math.min(1.0f, (float) (entity.getHealth() / entity.getMaxHealth()))));

                            AABB area = entity.getBoundingBox().inflate(32.0);
                            for (ServerPlayer p : entity.level().getEntitiesOfClass(ServerPlayer.class, area)) {
                                bossEvent.addPlayer(p);
                            }
                        }
                    }
                }
            }
        }

        private BossEvent.BossBarColor parseBossColor(String c) {
            if (c == null) return BossEvent.BossBarColor.RED;
            return switch (c.toUpperCase()) {
                case "PINK" -> BossEvent.BossBarColor.PINK;
                case "BLUE" -> BossEvent.BossBarColor.BLUE;
                case "GREEN" -> BossEvent.BossBarColor.GREEN;
                case "YELLOW" -> BossEvent.BossBarColor.YELLOW;
                case "PURPLE" -> BossEvent.BossBarColor.PURPLE;
                case "WHITE" -> BossEvent.BossBarColor.WHITE;
                default -> BossEvent.BossBarColor.RED;
            };
        }

        private BossEvent.BossBarOverlay parseBossOverlay(String o) {
            if (o == null) return BossEvent.BossBarOverlay.PROGRESS;
            return switch (o.toUpperCase()) {
                case "NOTCHED_6" -> BossEvent.BossBarOverlay.NOTCHED_6;
                case "NOTCHED_10" -> BossEvent.BossBarOverlay.NOTCHED_10;
                case "NOTCHED_12" -> BossEvent.BossBarOverlay.NOTCHED_12;
                case "NOTCHED_20" -> BossEvent.BossBarOverlay.NOTCHED_20;
                default -> BossEvent.BossBarOverlay.PROGRESS;
            };
        }
    }
}
