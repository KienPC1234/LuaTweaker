package com.luatweaker.platform.entity;

import com.luatweaker.api.entity.IEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public class NeoForgeEntityWrapper implements IEntity {

    private final Entity entity;

    public NeoForgeEntityWrapper(Entity entity) {
        this.entity = entity;
    }

    @Override
    public String getType() {
        return entity != null ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : "";
    }

    @Override
    public String getName() {
        return entity != null ? entity.getName().getString() : "Unknown";
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
    public float getMaxHealth() {
        return (entity instanceof LivingEntity living) ? living.getMaxHealth() : 0.0f;
    }

    @Override
    public void damage(float amount) {
        if (entity instanceof LivingEntity living) {
            living.hurt(living.damageSources().generic(), amount);
        }
    }

    @Override
    public void heal(float amount) {
        if (entity instanceof LivingEntity living) {
            living.heal(amount);
        }
    }

    @Override
    public void addEffect(String effectId, int durationTicks, int amplifier) {
        if (entity instanceof LivingEntity living && effectId != null && !effectId.isBlank()) {
            ResourceLocation rl = effectId.contains(":") ? ResourceLocation.parse(effectId) : ResourceLocation.fromNamespaceAndPath("minecraft", effectId);
            Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);
            if (effectHolder != null) {
                living.addEffect(new MobEffectInstance(effectHolder, durationTicks, amplifier));
            }
        }
    }

    @Override
    public void removeEffect(String effectId) {
        if (entity instanceof LivingEntity living && effectId != null && !effectId.isBlank()) {
            ResourceLocation rl = effectId.contains(":") ? ResourceLocation.parse(effectId) : ResourceLocation.fromNamespaceAndPath("minecraft", effectId);
            BuiltInRegistries.MOB_EFFECT.getHolder(rl).ifPresent(living::removeEffect);
        }
    }

    @Override
    public void removeAllEffects() {
        if (entity instanceof LivingEntity living) {
            living.removeAllEffects();
        }
    }

    @Override
    public boolean hasEffect(String effectId) {
        if (entity instanceof LivingEntity living && effectId != null && !effectId.isBlank()) {
            ResourceLocation rl = effectId.contains(":") ? ResourceLocation.parse(effectId) : ResourceLocation.fromNamespaceAndPath("minecraft", effectId);
            return BuiltInRegistries.MOB_EFFECT.getHolder(rl).map(living::hasEffect).orElse(false);
        }
        return false;
    }

    @Override
    public boolean isAlive() {
        return entity != null && entity.isAlive();
    }

    @Override
    public void setIgniteSeconds(int seconds) {
        if (entity != null) {
            entity.igniteForSeconds(seconds);
        }
    }

    @Override
    public void extinguish() {
        if (entity != null) {
            entity.clearFire();
        }
    }

    @Override
    public void playSound(String soundId, float volume, float pitch) {
        if (entity != null && soundId != null && !soundId.isBlank()) {
            ResourceLocation rl = soundId.contains(":") ? ResourceLocation.parse(soundId) : ResourceLocation.fromNamespaceAndPath("minecraft", soundId);
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(rl);
            if (sound != null) {
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
            }
        }
    }

    @Override
    public void spawnParticle(String particleId, int count, double speed) {
        if (entity != null && entity.level() instanceof ServerLevel serverLevel && particleId != null) {
            ResourceLocation rl = particleId.contains(":") ? ResourceLocation.parse(particleId) : ResourceLocation.fromNamespaceAndPath("minecraft", particleId);
            ParticleType<?> pType = BuiltInRegistries.PARTICLE_TYPE.get(rl);
            if (pType instanceof net.minecraft.core.particles.SimpleParticleType simple) {
                serverLevel.sendParticles(simple, entity.getX(), entity.getY() + 1.0, entity.getZ(), Math.max(1, count), 0.2, 0.2, 0.2, speed);
            }
        }
    }

    @Override
    public void teleport(double x, double y, double z) {
        if (entity != null) {
            entity.teleportTo(x, y, z);
        }
    }

    @Override
    public void setMotion(double vx, double vy, double vz) {
        if (entity != null) {
            entity.setDeltaMovement(vx, vy, vz);
            entity.hasImpulse = true;
        }
    }

    @Override
    public void addVelocity(double vx, double vy, double vz) {
        if (entity != null) {
            entity.push(vx, vy, vz);
            entity.hasImpulse = true;
        }
    }

    @Override public double getX() { return entity != null ? entity.getX() : 0; }
    @Override public double getY() { return entity != null ? entity.getY() : 0; }
    @Override public double getZ() { return entity != null ? entity.getZ() : 0; }
    @Override public float getYaw() { return entity != null ? entity.getYRot() : 0; }
    @Override public float getPitch() { return entity != null ? entity.getXRot() : 0; }

    @Override
    public void setYaw(float yaw) {
        if (entity != null) entity.setYRot(yaw);
    }

    @Override
    public void setPitch(float pitch) {
        if (entity != null) entity.setXRot(pitch);
    }

    @Override public boolean isSneaking() { return entity != null && entity.isShiftKeyDown(); }
    @Override public void setSneaking(boolean value) { if (entity != null) entity.setShiftKeyDown(value); }
    @Override public boolean isSprinting() { return entity != null && entity.isSprinting(); }
    @Override public void setSprinting(boolean value) { if (entity != null) entity.setSprinting(value); }
    @Override public boolean isGlowing() { return entity != null && entity.isCurrentlyGlowing(); }
    @Override public void setGlowing(boolean value) { if (entity != null) entity.setGlowingTag(value); }
    @Override public boolean isInvulnerable() { return entity != null && entity.isInvulnerable(); }
    @Override public void setInvulnerable(boolean value) { if (entity != null) entity.setInvulnerable(value); }
    @Override public boolean isInWater() { return entity != null && entity.isInWater(); }
    @Override public boolean isInLava() { return entity != null && entity.isInLava(); }
    @Override public boolean isOnGround() { return entity != null && entity.onGround(); }

    @Override public String getCustomName() { return (entity != null && entity.hasCustomName()) ? entity.getCustomName().getString() : ""; }
    @Override public void setCustomName(String name) { if (entity != null && name != null) entity.setCustomName(Component.literal(name)); }
    @Override public boolean isCustomNameVisible() { return entity != null && entity.isCustomNameVisible(); }
    @Override public void setCustomNameVisible(boolean visible) { if (entity != null) entity.setCustomNameVisible(visible); }

    @Override public void addTag(String tag) { if (entity != null && tag != null) entity.addTag(tag); }
    @Override public void removeTag(String tag) { if (entity != null && tag != null) entity.removeTag(tag); }
    @Override public boolean hasTag(String tag) { return entity != null && tag != null && entity.getTags().contains(tag); }

    @Override public void swingArm() {
        if (entity instanceof LivingEntity living) living.swing(InteractionHand.MAIN_HAND);
    }

    @Override public void setTarget(IEntity target) {
        if (entity instanceof Mob mob) {
            if (target != null && target.getRawEntity() instanceof LivingEntity livingTarget) {
                mob.setTarget(livingTarget);
            } else {
                mob.setTarget(null);
            }
        }
    }

    @Override public boolean isPlayer() { return entity instanceof Player; }
    @Override public boolean isLiving() { return entity instanceof LivingEntity; }

    @Override
    public void remove() {
        if (entity != null) {
            entity.discard();
        }
    }

    @Override
    public Object getRawEntity() {
        return entity;
    }
}
