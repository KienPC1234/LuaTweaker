package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.ai.IAIGoalService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

public class AIGoalServiceImpl implements IAIGoalService {
    private final ILuaEngine engine;

    public AIGoalServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void addGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable) {
        Platform.getEntity().addCustomGoal(entity, priority, goalTable, engine, false);
    }

    @Override
    public void addTargetGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable) {
        Platform.getEntity().addCustomGoal(entity, priority, goalTable, engine, true);
    }

    @Override
    public void removeGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable) {
        Platform.getEntity().removeCustomGoal(entity, goalTable);
    }

    @Override
    public void clearGoals(@NotNull IEntity entity) {
        Platform.getEntity().clearCustomGoals(entity);
    }

    @Override
    public void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType) {
        Platform.getEntity().addNearestAttackableTargetGoal(entity, priority, targetType);
    }

    @Override
    public void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {
        Platform.getEntity().addHurtByTargetGoal(entity, priority);
    }

    @Override
    public void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {
        Platform.getEntity().addMeleeAttackGoal(entity, priority, speed, pauseWhenMobIdle);
    }

    @Override
    public void addFloatGoal(@NotNull IEntity entity, int priority) {
        Platform.getEntity().addFloatGoal(entity, priority);
    }

    @Override
    public void addWanderGoal(@NotNull IEntity entity, int priority, double speed) {
        Platform.getEntity().addWanderGoal(entity, priority, speed);
    }

    @Override
    public void addLookAtGoal(@NotNull IEntity entity, int priority, double distance, @NotNull String targetType) {
        Platform.getEntity().addLookAtGoal(entity, priority, distance, targetType);
    }

    @Override
    public void addLookAroundGoal(@NotNull IEntity entity, int priority) {
        Platform.getEntity().addLookAroundGoal(entity, priority);
    }

    @Override
    public void addPanicGoal(@NotNull IEntity entity, int priority, double speed) {
        Platform.getEntity().addPanicGoal(entity, priority, speed);
    }

    @Override
    public void addAvoidGoal(@NotNull IEntity entity, int priority, @NotNull String targetType,
                             double distance, double walkSpeed, double sprintSpeed) {
        Platform.getEntity().addAvoidGoal(entity, priority, targetType, distance, walkSpeed, sprintSpeed);
    }

    @Override
    public void addLeapGoal(@NotNull IEntity entity, int priority, double strength) {
        Platform.getEntity().addLeapGoal(entity, priority, strength);
    }

    @Override
    public void addBreakDoorGoal(@NotNull IEntity entity, int priority, boolean hardOnly) {
        Platform.getEntity().addBreakDoorGoal(entity, priority, hardOnly);
    }

    @Override
    public void addTemptGoal(@NotNull IEntity entity, int priority, double speed, @NotNull String itemId) {
        Platform.getEntity().addTemptGoal(entity, priority, speed, itemId);
    }

    @Override
    public void addMoveToTargetGoal(@NotNull IEntity entity, int priority, double speed, double maxDistance) {
        Platform.getEntity().addMoveToTargetGoal(entity, priority, speed, maxDistance);
    }

    @Override
    public java.util.@NotNull List<java.util.Map<String, Object>> listGoals(@NotNull IEntity entity) {
        return Platform.getEntity().listGoals(entity);
    }
}
