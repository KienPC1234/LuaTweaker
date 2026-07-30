package com.luatweaker.platform.entity;

import com.luatweaker.api.entity.IEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
    public void removeAllEffects() {
        if (entity instanceof LivingEntity living) {
            living.removeAllEffects();
        }
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

    @Override public double getX() { return entity != null ? entity.getX() : 0; }
    @Override public double getY() { return entity != null ? entity.getY() : 0; }
    @Override public double getZ() { return entity != null ? entity.getZ() : 0; }

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
