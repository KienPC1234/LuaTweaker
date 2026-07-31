package com.luatweaker.platform.interaction;

import com.luatweaker.api.interaction.IInteractableEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class NeoForgeInteractableEntity implements IInteractableEntity {
    private final Entity entity;

    public NeoForgeInteractableEntity(@NotNull Entity entity) {
        this.entity = entity;
    }

    @Override
    @NotNull
    public String getId() {
        return entity.getUUID().toString();
    }

    @Override
    @NotNull
    public String getName() {
        return entity.getName().getString();
    }

    @Override
    @NotNull
    public String getType() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    @Override
    public double getX() {
        return entity.getX();
    }

    @Override
    public double getY() {
        return entity.getY();
    }

    @Override
    public double getZ() {
        return entity.getZ();
    }

    @Override
    public void setPosition(double x, double y, double z) {
        entity.teleportTo(x, y, z);
    }

    @Override
    public float getHealth() {
        return (entity instanceof LivingEntity living) ? living.getHealth() : 0.0f;
    }

    @Override
    public void setHealth(float health) {
        if (entity instanceof LivingEntity living) {
            living.setHealth(health);
        }
    }

    @Override
    public void lookAt(double x, double y, double z) {
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

    @Override
    public void lookAt(@NotNull Object targetEntity) {
        if (targetEntity instanceof Entity mcTarget) {
            if (entity instanceof Mob mob) {
                mob.getLookControl().setLookAt(mcTarget, 10.0f, (float) mob.getMaxHeadXRot());
            } else {
                lookAt(mcTarget.getX(), mcTarget.getY() + mcTarget.getEyeHeight(), mcTarget.getZ());
            }
        }
    }

    @Override
    public void SendMessage(@NotNull String message) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }

    @Override
    public void SendTitle(@NotNull String title, @NotNull String subtitle) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(net.minecraft.network.chat.Component.literal(title)));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(net.minecraft.network.chat.Component.literal(subtitle)));
        }
    }

    @Override
    public void SendOverlayMessage(@NotNull String message) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
        }
    }

    @Override
    public boolean GiveItem(@NotNull String itemId, int count) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item != null) {
                return player.getInventory().add(new net.minecraft.world.item.ItemStack(item, count));
            }
        }
        return false;
    }

    @Override
    public float getMaxHealth() {
        return (entity instanceof LivingEntity living) ? living.getMaxHealth() : 0.0f;
    }

    @Override
    public void setMaxHealth(float maxHealth) {
        if (entity instanceof LivingEntity living) {
            var attr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(maxHealth);
            }
        }
    }

    @Override
    public boolean isAlive() {
        return entity.isAlive();
    }

    @Override
    public boolean isOnFire() {
        return entity.isOnFire();
    }

    @Override
    public void setOnFire(boolean onFire) {
        if (onFire) {
            entity.setRemainingFireTicks(100);
        } else {
            entity.clearFire();
        }
    }

    @Override
    public boolean isSneaking() {
        return entity.isCrouching();
    }

    @Override
    public void setSneaking(boolean sneaking) {
        entity.setShiftKeyDown(sneaking);
    }

    @Override
    public boolean isSprinting() {
        return entity.isSprinting();
    }

    @Override
    public void setSprinting(boolean sprinting) {
        entity.setSprinting(sprinting);
    }

    @Override
    @NotNull
    public String getCustomName() {
        return entity.hasCustomName() && entity.getCustomName() != null ?
            entity.getCustomName().getString() : getName();
    }

    @Override
    public void setCustomName(@NotNull String name) {
        entity.setCustomName(net.minecraft.network.chat.Component.literal(name));
        entity.setCustomNameVisible(true);
    }

    @Override
    public double getVx() {
        return entity.getDeltaMovement().x;
    }

    @Override
    public double getVy() {
        return entity.getDeltaMovement().y;
    }

    @Override
    public double getVz() {
        return entity.getDeltaMovement().z;
    }

    @Override
    public void setVelocity(double vx, double vy, double vz) {
        entity.setDeltaMovement(vx, vy, vz);
        entity.hasImpulse = true;
    }

    @Override
    @NotNull
    public String getNbt() {
        return entity.getPersistentData().toString();
    }

    @Override
    public void setNbt(@NotNull String nbtJson) {
        try {
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtJson);
            entity.getPersistentData().merge(tag);
        } catch (Exception ignored) {}
    }

    @Override
    public String getAttribute(@NotNull String key) {
        if (entity.getPersistentData().contains(key)) {
            return entity.getPersistentData().get(key).getAsString();
        }
        return null;
    }

    @Override
    public void setAttribute(@NotNull String key, @NotNull String value) {
        entity.getPersistentData().putString(key, value);
    }

    @Override
    public void ShootProjectile(@NotNull String projectileTypeId, double speed, double inaccuracy) {
        if (entity instanceof LivingEntity shooter && shooter.level() instanceof ServerLevel level) {
            Vec3 look = shooter.getLookAngle();
            Vec3 spawnPos = shooter.getEyePosition().add(look.scale(1.2));
            spawnProjectile(level, shooter, spawnPos, look, projectileTypeId, speed, inaccuracy);
        }
    }

    @Override
    public void ShootProjectileAt(@NotNull String projectileTypeId, @NotNull Object targetEntity, double speed) {
        if (entity instanceof LivingEntity shooter && shooter.level() instanceof ServerLevel level) {
            Vec3 targetPos = null;
            if (targetEntity instanceof Entity t) {
                targetPos = t.getEyePosition();
            } else if (targetEntity instanceof IInteractableEntity ie) {
                targetPos = new Vec3(ie.getX(), ie.getY() + 1.0, ie.getZ());
            }
            if (targetPos != null) {
                Vec3 shooterPos = shooter.getEyePosition();
                Vec3 dir = targetPos.subtract(shooterPos).normalize();
                Vec3 spawnPos = shooterPos.add(dir.scale(1.2));
                spawnProjectile(level, shooter, spawnPos, dir, projectileTypeId, speed, 0.0);
            }
        }
    }

    private void spawnProjectile(ServerLevel level, LivingEntity shooter, Vec3 spawnPos, Vec3 dir, String typeId, double speed, double inaccuracy) {
        String cleanId = typeId.contains(":") ? typeId : "minecraft:" + typeId;
        switch (cleanId) {
            case "minecraft:small_fireball": {
                var projectile = new net.minecraft.world.entity.projectile.SmallFireball(level, shooter, dir.scale(speed));
                projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                level.addFreshEntity(projectile);
                break;
            }
            case "minecraft:fireball":
            case "minecraft:large_fireball": {
                var projectile = new net.minecraft.world.entity.projectile.LargeFireball(level, shooter, dir.scale(speed), 1);
                projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                level.addFreshEntity(projectile);
                break;
            }
            case "minecraft:dragon_fireball": {
                var projectile = new net.minecraft.world.entity.projectile.DragonFireball(level, shooter, dir.scale(speed));
                projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                level.addFreshEntity(projectile);
                break;
            }
            case "minecraft:wither_skull": {
                var projectile = new net.minecraft.world.entity.projectile.WitherSkull(level, shooter, dir.scale(speed));
                projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                level.addFreshEntity(projectile);
                break;
            }
            case "minecraft:snowball": {
                var projectile = new net.minecraft.world.entity.projectile.Snowball(level, shooter);
                projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                projectile.shoot(dir.x, dir.y, dir.z, (float) speed, (float) inaccuracy);
                level.addFreshEntity(projectile);
                break;
            }
            default: {
                var projectile = new net.minecraft.world.entity.projectile.Arrow(level, shooter, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
                projectile.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
                projectile.shoot(dir.x, dir.y, dir.z, (float) speed, (float) inaccuracy);
                level.addFreshEntity(projectile);
                break;
            }
        }
    }

    @Override
    public void PlayAnimation(@NotNull String animationName, double speed, double transitionLength) {
        entity.getPersistentData().putString("PlayingAnimation", animationName);
        entity.getPersistentData().putDouble("AnimSpeed", speed);
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[EntityAnimation] Played animation '" + animationName + "' on entity " + entity.getUUID() + " speed=" + speed);
    }

    @Override
    public void StopAnimation(@NotNull String animationName) {
        entity.getPersistentData().remove("PlayingAnimation");
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[EntityAnimation] Stopped animation '" + animationName + "' on entity " + entity.getUUID());
    }

    @Override
    public void Destroy() {
        entity.discard();
    }
}
