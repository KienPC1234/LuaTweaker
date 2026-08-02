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
                net.minecraft.world.phys.Vec3 look = living.getLookAngle();
                net.minecraft.world.phys.Vec3 spawnPos = living.getEyePosition().add(look.scale(1.2));
                net.minecraft.world.entity.projectile.Projectile projectile = createProjectile(serverLevel, living, projectileType);
                if (projectile != null) {
                    projectile.setOwner(living);
                    projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                    projectile.shootFromRotation(living, living.getXRot(), living.getYRot(), 0.0F, (float) speed, (float) inaccuracy);
                    serverLevel.addFreshEntity(projectile);
                    com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[Projectile] Fired projectile '" + projectileType + "' at pos (" + String.format("%.2f, %.2f, %.2f", spawnPos.x, spawnPos.y, spawnPos.z) + ") with speed " + speed
                    );
                } else {
                    com.luatweaker.api.log.LuaTweakerLog.get().warn(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[Projectile] createProjectile returned null for type '" + projectileType + "'"
                    );
                }
            } else {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[Projectile] Cannot fire '" + projectileType + "': shooter's level is not a ServerLevel (" + (level != null ? level.getClass().getSimpleName() : "null") + ")"
                );
            }
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Projectile] Cannot fire '" + projectileType + "': shooter raw entity is not a LivingEntity (" + (shooter.getRawEntity() != null ? shooter.getRawEntity().getClass().getSimpleName() : "null") + ")"
            );
        }
    }

    public static com.luatweaker.api.entity.IEntity shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed) {
        if (shooter.getRawEntity() instanceof net.minecraft.world.entity.LivingEntity living && target.getRawEntity() instanceof net.minecraft.world.entity.LivingEntity targetLiving) {
            living.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            net.minecraft.world.level.Level level = living.level();
            if (level instanceof ServerLevel serverLevel) {
                net.minecraft.world.phys.Vec3 look = living.getLookAngle();
                net.minecraft.world.phys.Vec3 spawnPos = living.getEyePosition().add(look.scale(1.2));
                net.minecraft.world.entity.projectile.Projectile projectile = createProjectile(serverLevel, living, projectileType);
                if (projectile != null) {
                    projectile.setOwner(living);
                    projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                    double dx = targetLiving.getX() - spawnPos.x;
                    double dy = targetLiving.getY(0.5) - spawnPos.y;
                    double dz = targetLiving.getZ() - spawnPos.z;
                    projectile.shoot(dx, dy, dz, (float) speed, 1.0F);
                    serverLevel.addFreshEntity(projectile);
                    com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[Projectile] Fired projectile '" + projectileType + "' at target '" + targetLiving.getDisplayName().getString() + "' with speed " + speed
                    );
                    return new com.luatweaker.platform.interaction.NeoForgeInteractableEntity(projectile);
                } else {
                    com.luatweaker.api.log.LuaTweakerLog.get().warn(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[Projectile] createProjectile returned null for type '" + projectileType + "'"
                    );
                }
            } else {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[Projectile] Cannot fire '" + projectileType + "': shooter's level is not a ServerLevel"
                );
            }
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Projectile] Cannot fire '" + projectileType + "': shooter or target is not a LivingEntity"
            );
        }
        return null;
    }

    private static net.minecraft.world.entity.projectile.Projectile createProjectile(ServerLevel level, net.minecraft.world.entity.LivingEntity shooter, String type) {
        net.minecraft.world.phys.Vec3 look = shooter.getLookAngle().scale(0.5);

        // Custom projectile definitions registered via Content.registerProjectile
        // take priority: explosionPower > 1 becomes a LargeFireball, otherwise a
        // SmallFireball; damage/trailParticle are recorded for the definition.
        com.luatweaker.api.content.ProjectileDefinition definition = com.luatweaker.content.ProjectileRegistry.get(type);
        if (definition != null) {
            if (definition.explosionPower() > 1.0) {
                return new net.minecraft.world.entity.projectile.LargeFireball(level, shooter, look, (int) Math.round(definition.explosionPower()));
            }
            return new net.minecraft.world.entity.projectile.SmallFireball(level, shooter, look);
        }

        if (type.contains("dragon_fireball")) {
            return new net.minecraft.world.entity.projectile.DragonFireball(level, shooter, look);
        } else if (type.contains("wither_skull")) {
            return new net.minecraft.world.entity.projectile.WitherSkull(level, shooter, look);
        } else if (type.contains("small_fireball") || type.contains("ruby_orb")) {
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

    public static Object spawnEntity(@NotNull String entityId, double x, double y, double z) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            net.minecraft.resources.ResourceLocation rl = entityId.contains(":") ? net.minecraft.resources.ResourceLocation.parse(entityId) : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("luatweaker", entityId);
            net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (type != null) {
                ServerLevel level = server.overworld();
                Entity entity = type.create(level);
                if (entity != null) {
                    entity.moveTo(x, y, z, 0.0f, 0.0f);
                    level.addFreshEntity(entity);
                    return entity;
                }
            }
        }
        return null;
    }
}
