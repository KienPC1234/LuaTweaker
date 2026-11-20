package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.EntityDrop;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Server-side lifecycle dispatch for custom entity types. All hooks are
 * per-type (matched by entity type id against the startup builders) and never
 * fire on the client, mirroring the item/block handler rule. Vanilla entities
 * are untouched: without a matching builder every handler is a no-op.
 */
public final class EntityHookDispatcher {
    private final IContentService contentService;
    private final Function<String, ResourceLocation> locationParser;
    private final Map<UUID, Long> lastTickByEntity = new ConcurrentHashMap<>();

    public EntityHookDispatcher(IContentService contentService,
                                Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.locationParser = locationParser;
    }

    private IEntityBuilder findBuilder(Entity entity) {
        if (entity == null) {
            return null;
        }
        ResourceLocation key;
        try {
            key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        } catch (Exception e) {
            return null;
        }
        if (key == null) {
            return null;
        }
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            try {
                if (key.equals(locationParser.apply(builder.getId()))) {
                    return builder;
                }
            } catch (Exception e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Entity hook dispatch: bad builder id '" + builder.getId() + "': " + e.getMessage());
            }
        }
        return null;
    }

    private static boolean isServer(Entity entity) {
        return entity != null && entity.level() != null && !entity.level().isClientSide();
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity living = event.getEntity();
        if (!isServer(living)) {
            return;
        }
        IEntityBuilder builder;
        try {
            builder = findBuilder(living);
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Entity onHurt dispatch failed: " + e.getMessage());
            return;
        }
        if (builder == null || builder.getOnHurtHandler() == null) {
            return;
        }
        try {
            IEntity entity = new com.luatweaker.platform.entity.NeoForgeEntityWrapper(living);
            IEntity attacker = null;
            if (event.getSource() != null && event.getSource().getEntity() instanceof LivingEntity livingAttacker) {
                attacker = new com.luatweaker.platform.entity.NeoForgeEntityWrapper(livingAttacker);
            }
            Float replacement = builder.getOnHurtHandler().handle(entity, event.getNewDamage(), attacker);
            if (replacement != null) {
                event.setNewDamage(Math.max(0.0f, replacement));
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Error executing entity onHurt hook for " + builder.getId() + ": " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!isServer(living)) {
            return;
        }
        IEntityBuilder builder;
        try {
            builder = findBuilder(living);
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Entity onDeath dispatch failed: " + e.getMessage());
            return;
        }
        if (builder == null || builder.getOnDeathHandler() == null) {
            return;
        }
        try {
            IEntity entity = new com.luatweaker.platform.entity.NeoForgeEntityWrapper(living);
            IEntity killer = null;
            if (event.getSource() != null && event.getSource().getEntity() instanceof LivingEntity livingKiller) {
                killer = new com.luatweaker.platform.entity.NeoForgeEntityWrapper(livingKiller);
            }
            if (builder.getOnDeathHandler().handle(entity, killer)) {
                event.setCanceled(true);
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Error executing entity onDeath hook for " + builder.getId() + ": " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity living = event.getEntity();
        if (!isServer(living) || !(living.level() instanceof ServerLevel level)) {
            return;
        }
        IEntityBuilder builder;
        try {
            builder = findBuilder(living);
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Entity drop dispatch failed: " + e.getMessage());
            return;
        }
        if (builder == null || builder.getDrops().isEmpty()) {
            return;
        }
        try {
            event.setCanceled(true);
            for (EntityDrop drop : builder.getDrops()) {
                ResourceLocation itemRl = drop.itemId().contains(":")
                        ? ResourceLocation.tryParse(drop.itemId())
                        : ResourceLocation.fromNamespaceAndPath("minecraft", drop.itemId());
                if (itemRl == null) {
                    LuaTweakerLog.get().warn(LogStage.SYSTEM,
                            "Entity drop skipped for " + builder.getId() + ": bad item id '" + drop.itemId() + "'");
                    continue;
                }
                var item = BuiltInRegistries.ITEM.getOptional(itemRl).orElse(null);
                if (item == null) {
                    LuaTweakerLog.get().warn(LogStage.SYSTEM,
                            "Entity drop skipped for " + builder.getId() + ": unknown item '" + drop.itemId() + "'");
                    continue;
                }
                int range = Math.max(0, drop.maxCount() - drop.minCount());
                int count = drop.minCount() + (range == 0 ? 0 : living.getRandom().nextInt(range + 1));
                ItemEntity dropEntity = new ItemEntity(level,
                        living.getX(), living.getY(), living.getZ(), new ItemStack(item, count));
                level.addFreshEntity(dropEntity);
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Error executing entity drops for " + builder.getId() + ": " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity living = event.getEntity();
        if (!isServer(living)) {
            return;
        }
        IEntityBuilder builder;
        try {
            builder = findBuilder(living);
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Entity experience dispatch failed: " + e.getMessage());
            return;
        }
        if (builder == null || !builder.hasExplicitExperience()) {
            return;
        }
        try {
            event.setDroppedExperience(Math.max(0, builder.getExperience()));
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Error executing entity experience for " + builder.getId() + ": " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel() == null || event.getLevel().isClientSide()) {
            return;
        }
        Entity target = event.getTarget();
        if (target == null) {
            return;
        }
        IEntityBuilder builder;
        try {
            builder = findBuilder(target);
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Entity onInteract dispatch failed: " + e.getMessage());
            return;
        }
        if (builder == null || builder.getOnInteractHandler() == null) {
            return;
        }
        try {
            IEntity entity = new com.luatweaker.platform.entity.NeoForgeEntityWrapper(target);
            IEntity rawPlayer = new com.luatweaker.platform.entity.NeoForgePlayerWrapper(event.getEntity());
            if (!(rawPlayer instanceof com.luatweaker.api.entity.IPlayer player)) {
                return;
            }
            if (builder.getOnInteractHandler().handle(entity, player)) {
                event.setCanceled(true);
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Error executing entity onInteract hook for " + builder.getId() + ": " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!isServer(entity)) {
            return;
        }
        IEntityBuilder builder;
        try {
            builder = findBuilder(entity);
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Entity onTick dispatch failed: " + e.getMessage());
            return;
        }
        if (builder == null || builder.getOnTickHandler() == null) {
            return;
        }
        int interval = Math.max(1, builder.getTickRate());
        long now = entity.level().getGameTime();
        UUID uuid = entity.getUUID();
        Long last = lastTickByEntity.get(uuid);
        if (last != null && now - last < interval) {
            return;
        }
        lastTickByEntity.put(uuid, now);
        if (entity.isRemoved() || (entity instanceof LivingEntity living && !living.isAlive())) {
            lastTickByEntity.remove(uuid);
            return;
        }
        try {
            builder.getOnTickHandler().handle(
                    new com.luatweaker.platform.entity.NeoForgeEntityWrapper(entity));
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Error executing entity onTick hook for " + builder.getId() + ": " + e.getMessage());
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() == null || event.getLevel().isClientSide() || event.getEntity() == null) {
            return;
        }
        // Forget stale throttle state so a respawned/reloaded entity with a
        // recycled UUID ticks immediately instead of inheriting a stale stamp.
        lastTickByEntity.remove(event.getEntity().getUUID());
    }
}
