package com.luatweaker.common.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.IInteractableEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public final class EntityInteractionHelper {
    private static final String PROJECTILE_FIRE_ERROR_PREFIX = "[Projectile] Cannot fire '";

    private EntityInteractionHelper() {}

    public static void lookAt(@NotNull IEntity actor, double x, double y, double z) {
        if (actor.getRawEntity() instanceof Entity entity) {
            if (entity instanceof Mob mob) {
                mob.getLookControl().setLookAt(x, y, z);
            } else {
                double dx = x - entity.getX();
                double dy = y - (entity.getY() + entity.getEyeHeight());
                double dz = z - entity.getZ();
                double dh = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
                entity.setYRot(yaw);
                entity.setXRot(pitch);
                entity.setYHeadRot(yaw);
            }
        }
    }

    public static void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
        if (actor.getRawEntity() instanceof Entity entity && target.getRawEntity() instanceof Entity targetEntity) {
            if (entity instanceof Mob mob) {
                mob.getLookControl().setLookAt(targetEntity, 10.0f, (float) mob.getMaxHeadXRot());
            } else {
                lookAt(actor, targetEntity.getX(), targetEntity.getY() + targetEntity.getEyeHeight(), targetEntity.getZ());
            }
        }
    }

    @Nullable
    public static IInteractableEntity getInteractableEntity(@NotNull String uuid) {
        return EntitySpawner.findInteractable(com.luatweaker.common.CommonServerHolder.get(), UUID.fromString(uuid));
    }

    public static void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed,
                                       double inaccuracy) {
        if (!(shooter.getRawEntity() instanceof LivingEntity living)) {
            warn(PROJECTILE_FIRE_ERROR_PREFIX + projectileType + "': shooter raw entity is not a LivingEntity");
            return;
        }
        living.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        boolean serverSide = ServerLevels.onServer(living.level(),
                level -> fireFromRotation(level, living, projectileType, speed, inaccuracy));
        if (!serverSide) {
            warn(PROJECTILE_FIRE_ERROR_PREFIX + projectileType + "': shooter's level is not a ServerLevel");
        }
    }

    private static void fireFromRotation(ServerLevel level, LivingEntity living, String projectileType,
                                         double speed, double inaccuracy) {
        net.minecraft.world.phys.Vec3 spawnPos = living.getEyePosition().add(living.getLookAngle().scale(1.2));
        net.minecraft.world.entity.projectile.Projectile projectile =
                ProjectileFactory.create(level, living, projectileType);
        projectile.setOwner(living);
        projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        projectile.shootFromRotation(living, living.getXRot(), living.getYRot(), 0.0F,
                (float) speed, (float) inaccuracy);
        level.addFreshEntity(projectile);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Projectile] Fired projectile '" + projectileType + "' at pos ("
                        + String.format("%.2f, %.2f, %.2f", spawnPos.x, spawnPos.y, spawnPos.z)
                        + ") with speed " + speed);
    }

    @Nullable
    public static IEntity shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType,
                                            @NotNull IEntity target, double speed) {
        if (!(shooter.getRawEntity() instanceof LivingEntity living)
                || !(target.getRawEntity() instanceof LivingEntity targetLiving)) {
            warn(PROJECTILE_FIRE_ERROR_PREFIX + projectileType + "': shooter or target is not a LivingEntity");
            return null;
        }
        living.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (!(living.level() instanceof ServerLevel)) {
            warn(PROJECTILE_FIRE_ERROR_PREFIX + projectileType + "': shooter's level is not a ServerLevel");
            return null;
        }
        return ServerLevels.map(ServerLevels.asServer(living.level()), null,
                level -> fireAtTarget(level, living, projectileType, targetLiving, speed));
    }

    @NotNull
    private static IEntity fireAtTarget(ServerLevel level, LivingEntity living, String projectileType,
                                        LivingEntity targetLiving, double speed) {
        net.minecraft.world.phys.Vec3 spawnPos = living.getEyePosition().add(living.getLookAngle().scale(1.2));
        net.minecraft.world.entity.projectile.Projectile projectile =
                ProjectileFactory.create(level, living, projectileType);
        projectile.setOwner(living);
        projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        projectile.shoot(targetLiving.getX() - spawnPos.x,
                targetLiving.getY(0.5) - spawnPos.y,
                targetLiving.getZ() - spawnPos.z, (float) speed, 1.0F);
        level.addFreshEntity(projectile);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Projectile] Fired projectile '" + projectileType + "' at target '"
                        + targetLiving.getDisplayName().getString() + "' with speed " + speed);
        return new MinecraftInteractableEntity(projectile);
    }

    public static java.util.Optional<net.minecraft.world.entity.EntityType<?>> resolveEntityType(String entityId) {
        return EntitySpawner.resolveEntityType(entityId);
    }

    public static Object spawnEntity(@NotNull String entityId, double x, double y, double z) {
        return EntitySpawner.spawnEntity(entityId, x, y, z);
    }

    private static void warn(@NotNull String message) {
        com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, message);
    }
}


