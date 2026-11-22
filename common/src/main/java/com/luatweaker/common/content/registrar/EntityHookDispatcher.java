package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.entities.EntityHookSanitizers;
import com.luatweaker.common.entity.MinecraftEntityWrapper;
import com.luatweaker.common.entity.MinecraftPlayerWrapper;
import com.luatweaker.common.util.CallbackFirewall;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Server-side lifecycle dispatch for custom entity types. All hooks are
 * per-type (matched by entity type id against the startup builders) and never
 * fire on the client, mirroring the item/block handler rule.
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

    public float onLivingHurt(LivingEntity living, DamageSource source, float damage) {
        if (!EntityBuilderLookup.isServer(living)) {
            return damage;
        }
        IEntityBuilder builder = builderFor(living, "onHurt");
        if (builder == null || builder.getOnHurtHandler() == null) {
            return damage;
        }
        return CallbackFirewall.supply(() -> {
            IEntity entity = new MinecraftEntityWrapper(living);
            IEntity attacker = source != null && source.getEntity() instanceof LivingEntity livingAttacker
                    ? new MinecraftEntityWrapper(livingAttacker)
                    : null;
            Float replacement = builder.getOnHurtHandler().handle(entity, damage, attacker);
            return EntityHookSanitizers.sanitizeReplacementDamage(replacement, damage);
        }, damage, LogStage.SYSTEM, () -> "Error executing entity onHurt hook for " + builder.getId());
    }

    public boolean onLivingDeath(LivingEntity living, DamageSource source) {
        if (!EntityBuilderLookup.isServer(living)) {
            return false;
        }
        IEntityBuilder builder = builderFor(living, "onDeath");
        if (builder == null || builder.getOnDeathHandler() == null) {
            return false;
        }
        return Boolean.TRUE.equals(CallbackFirewall.supply(() -> {
            IEntity killer = source != null && source.getEntity() instanceof LivingEntity livingKiller
                    ? new MinecraftEntityWrapper(livingKiller)
                    : null;
            if (builder.getOnDeathHandler().handle(new MinecraftEntityWrapper(living), killer)) {
                living.setHealth(EntityHookSanitizers.cancelDeathHealth(living.getHealth()));
                return true;
            }
            return false;
        }, false, LogStage.SYSTEM, () -> "Error executing entity onDeath hook for " + builder.getId()));
    }

    public boolean onEntityInteract(Player player, Entity target) {
        if (target == null || player == null || target.level().isClientSide()) {
            return false;
        }
        IEntityBuilder builder = builderFor(target, "onInteract");
        if (builder == null || builder.getOnInteractHandler() == null) {
            return false;
        }
        com.luatweaker.api.entity.IPlayer playerWrapper = new MinecraftPlayerWrapper(player);
        return Boolean.TRUE.equals(CallbackFirewall.supply(() ->
                builder.getOnInteractHandler().handle(new MinecraftEntityWrapper(target), playerWrapper),
                false, LogStage.SYSTEM, () -> "Error executing entity onInteract hook for " + builder.getId()));
    }

    public void onEntityLeaveLevel(Entity entity) {
        if (entity != null) {
            lastTickByEntity.remove(entity.getUUID());
        }
    }

    public void onEntityTick(Entity entity) {
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
        CallbackFirewall.run(() -> builder.getOnTickHandler().handle(new MinecraftEntityWrapper(entity)),
                LogStage.SYSTEM,
                () -> "Error executing entity onTick hook for " + builder.getId());
    }

    public void onEntityJoinLevel(Entity entity) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        lastTickByEntity.remove(entity.getUUID());
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
