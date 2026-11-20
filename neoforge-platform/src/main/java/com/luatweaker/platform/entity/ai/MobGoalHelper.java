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

    public static void addFloatGoal(@NotNull IEntity entity, int priority) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.Mob mob) {
            mob.goalSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.FloatGoal(mob));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add FloatGoal: Entity " + entity + " is not a Mob!");
        }
    }

    public static void addWanderGoal(@NotNull IEntity entity, int priority, double speed) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            mob.goalSelector.addGoal(priority,
                    new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal(mob, speed));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add WanderGoal: Entity " + entity + " is not a PathfinderMob!");
        }
    }

    public static void addLookAtGoal(@NotNull IEntity entity, int priority, double distance, @NotNull String targetType) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.Mob mob) {
            Class<? extends net.minecraft.world.entity.LivingEntity> clazz = resolveTargetClass(targetType, entity);
            if (clazz == null) {
                return;
            }
            mob.goalSelector.addGoal(priority,
                    new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(mob, clazz, (float) distance));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add LookAtGoal: Entity " + entity + " is not a Mob!");
        }
    }

    public static void addLookAroundGoal(@NotNull IEntity entity, int priority) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.Mob mob) {
            mob.goalSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(mob));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add LookAroundGoal: Entity " + entity + " is not a Mob!");
        }
    }

    public static void addPanicGoal(@NotNull IEntity entity, int priority, double speed) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            mob.goalSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.PanicGoal(mob, speed));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add PanicGoal: Entity " + entity + " is not a PathfinderMob!");
        }
    }

    public static void addAvoidGoal(@NotNull IEntity entity, int priority, @NotNull String targetType,
                                    double distance, double walkSpeed, double sprintSpeed) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            Class<? extends net.minecraft.world.entity.LivingEntity> clazz = resolveTargetClass(targetType, entity);
            if (clazz == null) {
                return;
            }
            mob.goalSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(
                    mob, clazz, (float) distance, walkSpeed, sprintSpeed));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add AvoidGoal: Entity " + entity + " is not a PathfinderMob!");
        }
    }

    public static void addLeapGoal(@NotNull IEntity entity, int priority, double strength) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.Mob mob) {
            mob.goalSelector.addGoal(priority,
                    new net.minecraft.world.entity.ai.goal.LeapAtTargetGoal(mob, (float) strength));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add LeapGoal: Entity " + entity + " is not a Mob!");
        }
    }

    public static void addBreakDoorGoal(@NotNull IEntity entity, int priority, boolean hardOnly) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.Mob mob) {
            mob.goalSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.BreakDoorGoal(mob,
                    difficulty -> !hardOnly || difficulty == net.minecraft.world.Difficulty.HARD));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add BreakDoorGoal: Entity " + entity + " is not a Mob!");
        }
    }

    public static void addTemptGoal(@NotNull IEntity entity, int priority, double speed, @NotNull String itemId) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            net.minecraft.resources.ResourceLocation rl = itemId.contains(":")
                    ? net.minecraft.resources.ResourceLocation.tryParse(itemId)
                    : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", itemId);
            net.minecraft.world.item.Item item = rl == null
                    ? null
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item == null) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "Cannot add TemptGoal: unknown item '" + itemId + "' for entity " + entity);
                return;
            }
            mob.goalSelector.addGoal(priority, new net.minecraft.world.entity.ai.goal.TemptGoal(
                    mob, speed, stack -> stack.getItem() == item, false));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add TemptGoal: Entity " + entity + " is not a PathfinderMob!");
        }
    }

    public static void addMoveToTargetGoal(@NotNull IEntity entity, int priority, double speed, double maxDistance) {
        if (entity.getRawEntity() instanceof net.minecraft.world.entity.PathfinderMob mob) {
            mob.goalSelector.addGoal(priority,
                    new net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal(mob, speed, (float) maxDistance));
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Cannot add MoveToTargetGoal: Entity " + entity + " is not a PathfinderMob!");
        }
    }

    /**
     * Shared target-class mapping for the look/avoid goals. Known names map to
     * exact classes; a resolvable entity id (vanilla or custom) falls back to
     * {@code LivingEntity} — broader than the named target, documented on the
     * API. Unknown names return {@code null} so callers warn and skip.
     */
    static Class<? extends net.minecraft.world.entity.LivingEntity> resolveTargetClass(
            @NotNull String targetTypeStr, @NotNull IEntity entity) {
        String lower = targetTypeStr == null ? "" : targetTypeStr.trim().toLowerCase();
        switch (lower) {
            case "player", "players" -> {
                return net.minecraft.world.entity.player.Player.class;
            }
            case "monster", "mob", "monsters", "mobs" -> {
                return net.minecraft.world.entity.monster.Monster.class;
            }
            case "animal", "animals" -> {
                return net.minecraft.world.entity.animal.Animal.class;
            }
            case "villager", "villagers" -> {
                return net.minecraft.world.entity.npc.AbstractVillager.class;
            }
            case "iron_golem", "golem" -> {
                return net.minecraft.world.entity.animal.IronGolem.class;
            }
            case "living" -> {
                return net.minecraft.world.entity.LivingEntity.class;
            }
            default -> {
                java.util.Optional<net.minecraft.world.entity.EntityType<?>> optType =
                        com.luatweaker.platform.interaction.EntityInteractionHelper.resolveEntityType(targetTypeStr);
                if (optType.isPresent()) {
                    com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM,
                            "Target type '" + targetTypeStr + "' matches a custom entity: "
                                    + "using LivingEntity (look/avoid applies to all living).");
                    return net.minecraft.world.entity.LivingEntity.class;
                }
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "Unknown target type '" + targetTypeStr + "' for entity " + entity);
                return null;
            }
        }
    }

    public static java.util.List<java.util.Map<String, Object>> listGoals(@NotNull IEntity entity) {
        if (!(entity.getRawEntity() instanceof net.minecraft.world.entity.Mob mob)) {
            return java.util.List.of();
        }
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        collectGoals(out, "goal", mob.goalSelector);
        collectGoals(out, "target", mob.targetSelector);
        return out;
    }

    private static void collectGoals(java.util.List<java.util.Map<String, Object>> out, String side,
                                     net.minecraft.world.entity.ai.goal.GoalSelector selector) {
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : selector.getAvailableGoals()) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("side", side);
            row.put("priority", wrapped.getPriority());
            // Custom Lua goals report their script-given name (or "LuaGoal") so
            // listGoals stays useful as a behavior-tree debugger; vanilla goals
            // keep their class simple name as before.
            if (wrapped.getGoal() instanceof NeoForgeLuaGoal luaGoal) {
                row.put("goal", "LuaGoal:" + luaGoal.getGoalName());
            } else {
                row.put("goal", wrapped.getGoal().getClass().getSimpleName());
            }
            row.put("running", wrapped.isRunning());
            out.add(row);
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
