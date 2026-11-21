package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import com.luatweaker.platform.util.CallbackFirewall;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
 *
 * <p>Every Lua callback runs through {@link CallbackFirewall} so one broken script
 * cannot abort the server tick.
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

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity living = event.getEntity();
        if (!EntityBuilderLookup.isServer(living)) {
            return;
        }
        IEntityBuilder builder = builderFor(living, "onHurt");
        if (builder == null || builder.getOnHurtHandler() == null) {
            return;
        }
        CallbackFirewall.run(() -> {
            IEntity entity = new NeoForgeEntityWrapper(living);
            IEntity attacker = event.getSource() != null
                    && event.getSource().getEntity() instanceof LivingEntity livingAttacker
                    ? new NeoForgeEntityWrapper(livingAttacker)
                    : null;
            Float replacement = builder.getOnHurtHandler().handle(entity, event.getNewDamage(), attacker);
            if (replacement != null) {
                event.setNewDamage(Math.max(0.0f, replacement));
            }
        }, LogStage.SYSTEM, () -> "Error executing entity onHurt hook for " + builder.getId());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!EntityBuilderLookup.isServer(living)) {
            return;
        }
        IEntityBuilder builder = builderFor(living, "onDeath");
        if (builder == null || builder.getOnDeathHandler() == null) {
            return;
        }
        CallbackFirewall.run(() -> {
            IEntity killer = event.getSource() != null
                    && event.getSource().getEntity() instanceof LivingEntity livingKiller
                    ? new NeoForgeEntityWrapper(livingKiller)
                    : null;
            if (builder.getOnDeathHandler().handle(new NeoForgeEntityWrapper(living), killer)) {
                event.setCanceled(true);
            }
        }, LogStage.SYSTEM, () -> "Error executing entity onDeath hook for " + builder.getId());
    }

    @SubscribeEvent
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity living = event.getEntity();
        if (!EntityBuilderLookup.isServer(living)) {
            return;
        }
        IEntityBuilder builder = builderFor(living, "experience");
        if (builder == null || !builder.hasExplicitExperience()) {
            return;
        }
        CallbackFirewall.run(() -> event.setDroppedExperience(Math.max(0, builder.getExperience())),
                LogStage.SYSTEM,
                () -> "Error executing entity experience for " + builder.getId());
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel() == null || event.getLevel().isClientSide() || event.getTarget() == null) {
            return;
        }
        Entity target = event.getTarget();
        IEntityBuilder builder = builderFor(target, "onInteract");
        if (builder == null || builder.getOnInteractHandler() == null) {
            return;
        }
        if (!(new NeoForgePlayerWrapper(event.getEntity()) instanceof com.luatweaker.api.entity.IPlayer player)) {
            return;
        }
        CallbackFirewall.run(() -> {
            if (builder.getOnInteractHandler().handle(new NeoForgeEntityWrapper(target), player)) {
                event.setCanceled(true);
            }
        }, LogStage.SYSTEM, () -> "Error executing entity onInteract hook for " + builder.getId());
    }

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!EntityBuilderLookup.isServer(entity)) {
            return;
        }
        IEntityBuilder builder = builderFor(entity, "onTick");
        if (builder == null || builder.getOnTickHandler() == null || isThrottled(entity, builder)) {
            return;
        }
        if (entity.isRemoved() || (entity instanceof LivingEntity living && !living.isAlive())) {
            lastTickByEntity.remove(entity.getUUID());
            return;
        }
        CallbackFirewall.run(() -> builder.getOnTickHandler().handle(new NeoForgeEntityWrapper(entity)),
                LogStage.SYSTEM,
                () -> "Error executing entity onTick hook for " + builder.getId());
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

    private boolean isThrottled(Entity entity, IEntityBuilder builder) {
        long now = entity.level().getGameTime();
        UUID uuid = entity.getUUID();
        Long last = lastTickByEntity.get(uuid);
        if (last != null && now - last < Math.max(1, builder.getTickRate())) {
            return true;
        }
        lastTickByEntity.put(uuid, now);
        return false;
    }

    private IEntityBuilder builderFor(Entity entity, String hook) {
        return CallbackFirewall.supply(
                () -> EntityBuilderLookup.find(contentService, locationParser, entity), null,
                LogStage.SYSTEM, () -> "Entity " + hook + " dispatch failed");
    }
}
