package com.luatweaker.platform.entity.ai;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public final class MobGoalHelper {
    private MobGoalHelper() {}

    public static void addCustomGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable, @NotNull ILuaEngine engine, boolean isTargetSelector) {
        if (entity.getRawEntity() instanceof Mob mob) {
            var goal = new NeoForgeLuaGoal(mob, goalTable, engine);
            if (isTargetSelector) {
                mob.targetSelector.addGoal(priority, goal);
            } else {
                mob.goalSelector.addGoal(priority, goal);
            }
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add custom AI goal: Entity " + entity + " is not a Mob!");
        }
    }

    public static void removeCustomGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable) {
        if (entity.getRawEntity() instanceof Mob mob) {
            removeGoalFromSelector(mob.goalSelector, goalTable);
            removeGoalFromSelector(mob.targetSelector, goalTable);
        }
    }

    private static void removeGoalFromSelector(net.minecraft.world.entity.ai.goal.GoalSelector selector, ILuaTable goalTable) {
        List<net.minecraft.world.entity.ai.goal.Goal> toRemove = new ArrayList<>();
        for (WrappedGoal wg : selector.getAvailableGoals()) {
            if (wg.getGoal() instanceof NeoForgeLuaGoal lGoal) {
                if (lGoal.getGoalTable().equals(goalTable)) {
                    toRemove.add(wg.getGoal());
                }
            }
        }
        for (net.minecraft.world.entity.ai.goal.Goal g : toRemove) {
            selector.removeGoal(g);
        }
    }

    public static void clearCustomGoals(@NotNull IEntity entity) {
        if (entity.getRawEntity() instanceof Mob mob) {
            clearAllGoalsFromSelector(mob.goalSelector);
            clearAllGoalsFromSelector(mob.targetSelector);
        }
    }

    private static void clearAllGoalsFromSelector(net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        List<net.minecraft.world.entity.ai.goal.Goal> toRemove = new ArrayList<>();
        for (WrappedGoal wg : selector.getAvailableGoals()) {
            toRemove.add(wg.getGoal());
        }
        for (net.minecraft.world.entity.ai.goal.Goal g : toRemove) {
            selector.removeGoal(g);
        }
    }

    public static void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            mob.goalSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(mob, speed, pauseWhenMobIdle));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add MeleeAttackGoal: Entity " + entity + " is not a PathfinderMob!");
        }
    }

    public static void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(mob));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add HurtByTargetGoal: Entity " + entity + " is not a PathfinderMob!");
        }
    }

    public static void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetTypeStr) {
        if (entity.getRawEntity() instanceof Mob mob) {
            String lower = targetTypeStr.trim().toLowerCase();
            if (lower.equals("player") || lower.equals("players")) {
                mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob, net.minecraft.world.entity.player.Player.class, true));
            } else if (lower.equals("monster") || lower.equals("mob") || lower.equals("monsters") || lower.equals("mobs")) {
                mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob, net.minecraft.world.entity.monster.Monster.class, true));
            } else if (lower.equals("animal") || lower.equals("animals")) {
                mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob, net.minecraft.world.entity.animal.Animal.class, true));
            } else if (lower.equals("villager") || lower.equals("villagers")) {
                mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob, net.minecraft.world.entity.npc.AbstractVillager.class, true));
            } else if (lower.equals("iron_golem") || lower.equals("golem")) {
                mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob, net.minecraft.world.entity.animal.IronGolem.class, true));
            } else if (lower.equals("living")) {
                mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(mob, net.minecraft.world.entity.LivingEntity.class, true));
            } else {
                java.util.Optional<net.minecraft.world.entity.EntityType<?>> optType =
                        com.luatweaker.platform.interaction.EntityInteractionHelper.resolveEntityType(targetTypeStr);
                if (optType.isPresent()) {
                    net.minecraft.world.entity.EntityType<?> expectedType = optType.get();
                    mob.targetSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                            mob, net.minecraft.world.entity.LivingEntity.class, 10, true, false, living -> living.getType() == expectedType));
                } else {
                    com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                            "Cannot add NearestAttackableTargetGoal: Unknown target type '" + targetTypeStr + "' for entity " + entity);
                }
            }
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add NearestAttackableTargetGoal: Entity " + entity + " is not a Mob!");
        }
    }
}
