package com.luatweaker.platform.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The single generic mob class for the humanoid/quadruped rigs. Unlike the
 * legacy {@code parent} rig (which instantiates a vanilla mob class and
 * inherits its hardcoded behavior), this class starts behavior-free: sounds
 * come from the builder snapshot and AI comes from the {@code defaultGoals}
 * preset plus Lua-added goals. Anything not configured does nothing.
 */
public class LuaCustomMob extends PathfinderMob {
    private static final String GOAL_PRESET_ANIMAL = "animal";
    private static final String GOAL_PRESET_MONSTER = "monster";

    public LuaCustomMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /**
     * Factory entry point matching {@link net.minecraft.world.entity.EntityType.EntityFactory}'s
     * {@code (EntityType<?>, Level)} shape. The raw cast is safe: the registrar
     * only ever passes the {@code EntityType<LuaCustomMob>} it is building.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Mob createGeneric(EntityType<?> type, Level level) {
        return new LuaCustomMob((EntityType<? extends PathfinderMob>) (EntityType) type, level);
    }

    @Override
    protected void registerGoals() {
        String preset = resolveDefaultGoals();
        if (GOAL_PRESET_ANIMAL.equals(preset)) {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new PanicGoal(this, 1.25));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        } else if (GOAL_PRESET_MONSTER.equals(preset)) {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
        // "none": empty selectors. The mob stands still until Lua adds goals.
    }

    private String resolveDefaultGoals() {
        String key = typeKey();
        LuaMobConfigs.Snapshot snapshot = key == null ? null : LuaMobConfigs.get(key);
        if (snapshot != null && snapshot.defaultGoals() != null) {
            return snapshot.defaultGoals();
        }
        return "none";
    }

    private @Nullable String typeKey() {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(this.getType());
        return key == null ? null : key.toString();
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return lookupSound("ambient");
    }

    @Override
    protected @Nullable SoundEvent getHurtSound(DamageSource damageSource) {
        SoundEvent configured = lookupSound("hurt");
        return configured != null ? configured : super.getHurtSound(damageSource);
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return lookupSound("death");
    }

    private @Nullable SoundEvent lookupSound(String slot) {
        String key = typeKey();
        LuaMobConfigs.Snapshot snapshot = key == null ? null : LuaMobConfigs.get(key);
        String soundId = null;
        if (snapshot != null) {
            soundId = switch (slot) {
                case "hurt" -> snapshot.hurtSound();
                case "death" -> snapshot.deathSound();
                default -> snapshot.ambientSound();
            };
        }
        if (soundId == null || soundId.isBlank()) {
            return null;
        }
        ResourceLocation rl = soundId.contains(":")
                ? ResourceLocation.tryParse(soundId)
                : ResourceLocation.tryParse("minecraft:" + soundId);
        if (rl == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Ignoring invalid " + slot + " sound id '" + soundId + "'");
            return null;
        }
        return BuiltInRegistries.SOUND_EVENT.getOptional(rl).orElse(null);
    }
}
