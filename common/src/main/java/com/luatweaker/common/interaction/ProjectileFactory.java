package com.luatweaker.common.interaction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Builds the projectile entity for {@code Entity:ShootProjectile}. */
final class ProjectileFactory {

    private static final double EXPLOSIVE_POWER_THRESHOLD = 1.0;

    private ProjectileFactory() {
    }

    @NotNull
    static Projectile create(@NotNull ServerLevel level, @NotNull LivingEntity shooter, @NotNull String type) {
        Vec3 look = shooter.getLookAngle().scale(0.5);
        // Custom projectile definitions registered via Content.registerProjectile
        // take priority: explosionPower > 1 becomes a LargeFireball, otherwise a
        // SmallFireball; damage/trailParticle are recorded for the definition.
        com.luatweaker.api.content.ProjectileDefinition definition = com.luatweaker.content.ProjectileRegistry.get(type);
        if (definition != null) {
            if (definition.explosionPower() > EXPLOSIVE_POWER_THRESHOLD) {
                return new net.minecraft.world.entity.projectile.LargeFireball(level, shooter, look,
                        (int) Math.round(definition.explosionPower()));
            }
            return new net.minecraft.world.entity.projectile.SmallFireball(level, shooter, look);
        }
        if (type.contains("dragon_fireball")) {
            return new net.minecraft.world.entity.projectile.DragonFireball(level, shooter, look);
        }
        if (type.contains("wither_skull")) {
            return new net.minecraft.world.entity.projectile.WitherSkull(level, shooter, look);
        }
        if (type.contains("small_fireball") || type.contains("ruby_orb")) {
            return new net.minecraft.world.entity.projectile.SmallFireball(level, shooter, look);
        }
        if (type.contains("snowball")) {
            return new net.minecraft.world.entity.projectile.Snowball(level, shooter);
        }
        return new net.minecraft.world.entity.projectile.LargeFireball(level, shooter, look, 2);
    }
}
