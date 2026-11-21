package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.ai.IAIGoalService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

/**
 * Movement/goal registrations half of {@link IAIGoalService}. Kept in a collaborator so
 * the public service class stays within a reviewable method count.
 */
abstract class AbstractAIGoalService implements IAIGoalService {

    protected final ILuaEngine engine;

    protected AbstractAIGoalService(@NotNull ILuaEngine engine) {
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
    public void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority,
                                               @NotNull String targetType) {
        Platform.getEntity().addNearestAttackableTargetGoal(entity, priority, targetType);
    }

    @Override
    public void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {
        Platform.getEntity().addHurtByTargetGoal(entity, priority);
    }

    @Override
    public void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed,
                                   boolean pauseWhenMobIdle) {
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
}
