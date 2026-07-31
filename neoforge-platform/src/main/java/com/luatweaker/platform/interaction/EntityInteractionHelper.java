package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.IInteractableEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public final class EntityInteractionHelper {
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
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            UUID id = UUID.fromString(uuid);
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    return new NeoForgeInteractableEntity(entity);
                }
            }
        }
        return null;
    }

    public static void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy) {
        if (shooter.getRawEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
            living.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            net.minecraft.world.level.Level level = living.level();
            if (level instanceof ServerLevel serverLevel) {
                net.minecraft.world.entity.projectile.Projectile projectile = createProjectile(serverLevel, living, projectileType);
                if (projectile != null) {
                    projectile.setOwner(living);
                    projectile.shootFromRotation(living, living.getXRot(), living.getYRot(), 0.0F, (float) speed, (float) inaccuracy);
                    serverLevel.addFreshEntity(projectile);
                }
            }
        }
    }

    public static void shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed) {
        if (shooter.getRawEntity() instanceof net.minecraft.world.entity.LivingEntity living && target.getRawEntity() instanceof net.minecraft.world.entity.LivingEntity targetLiving) {
            living.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            net.minecraft.world.level.Level level = living.level();
            if (level instanceof ServerLevel serverLevel) {
                net.minecraft.world.entity.projectile.Projectile projectile = createProjectile(serverLevel, living, projectileType);
                if (projectile != null) {
                    projectile.setOwner(living);
                    double dx = targetLiving.getX() - living.getX();
                    double dy = targetLiving.getY(0.5) - living.getY(0.5);
                    double dz = targetLiving.getZ() - living.getZ();
                    projectile.shoot(dx, dy, dz, (float) speed, 1.0F);
                    serverLevel.addFreshEntity(projectile);
                }
            }
        }
    }

    private static net.minecraft.world.entity.projectile.Projectile createProjectile(ServerLevel level, net.minecraft.world.entity.LivingEntity shooter, String type) {
        net.minecraft.world.phys.Vec3 look = shooter.getLookAngle();
        if (type.contains("dragon_fireball")) {
            return new net.minecraft.world.entity.projectile.DragonFireball(level, shooter, look);
        } else if (type.contains("wither_skull")) {
            return new net.minecraft.world.entity.projectile.WitherSkull(level, shooter, look);
        } else if (type.contains("small_fireball")) {
            return new net.minecraft.world.entity.projectile.SmallFireball(level, shooter, look);
        } else if (type.contains("snowball")) {
            return new net.minecraft.world.entity.projectile.Snowball(level, shooter);
        } else {
            return new net.minecraft.world.entity.projectile.LargeFireball(level, shooter, look, 2);
        }
    }

    public static void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
            living.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            living.getPersistentData().putString("CurrentAnimation", animName);
        }
    }
}
