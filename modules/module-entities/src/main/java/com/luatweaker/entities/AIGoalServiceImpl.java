package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.ai.IAIGoalService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Remaining per-entity goal registrations (look/leap/tempt/break-door) plus the goal
 * listing query. The shared movement registrations live in {@link AbstractAIGoalService}.
 */
public class AIGoalServiceImpl extends AbstractAIGoalService implements IAIGoalService {

    public AIGoalServiceImpl(@NotNull ILuaEngine engine) {
        super(engine);
    }

    @Override
    public void addLookAtGoal(@NotNull IEntity entity, int priority, double distance,
                              @NotNull String targetType) {
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
    public void addTemptGoal(@NotNull IEntity entity, int priority, double speed,
                             @NotNull String itemId) {
        Platform.getEntity().addTemptGoal(entity, priority, speed, itemId);
    }

    @Override
    public void addMoveToTargetGoal(@NotNull IEntity entity, int priority, double speed,
                                    double maxDistance) {
        Platform.getEntity().addMoveToTargetGoal(entity, priority, speed, maxDistance);
    }

    @Override
    public List<Map<String, Object>> listGoals(@NotNull IEntity entity) {
        return Platform.getEntity().listGoals(entity);
    }
}
