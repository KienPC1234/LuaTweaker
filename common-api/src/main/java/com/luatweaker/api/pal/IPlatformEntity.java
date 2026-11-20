package com.luatweaker.api.pal;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IPlatformEntity {
    void addCustomGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable, @NotNull ILuaEngine engine, boolean isTargetSelector);
    void removeCustomGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable);
    void clearCustomGoals(@NotNull IEntity entity);
    
    void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle);
    void addHurtByTargetGoal(@NotNull IEntity entity, int priority);
    void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType);
    void addFloatGoal(@NotNull IEntity entity, int priority);
    void addWanderGoal(@NotNull IEntity entity, int priority, double speed);
    void addLookAtGoal(@NotNull IEntity entity, int priority, double distance, @NotNull String targetType);
    void addLookAroundGoal(@NotNull IEntity entity, int priority);
    void addPanicGoal(@NotNull IEntity entity, int priority, double speed);
    void addAvoidGoal(@NotNull IEntity entity, int priority, @NotNull String targetType,
                      double distance, double walkSpeed, double sprintSpeed);
    void addLeapGoal(@NotNull IEntity entity, int priority, double strength);
    void addBreakDoorGoal(@NotNull IEntity entity, int priority, boolean hardOnly);
    void addTemptGoal(@NotNull IEntity entity, int priority, double speed, @NotNull String itemId);
    void addMoveToTargetGoal(@NotNull IEntity entity, int priority, double speed, double maxDistance);

    java.util.@NotNull List<java.util.Map<String, Object>> listGoals(@NotNull IEntity entity);

    @Nullable
    com.luatweaker.api.entity.IPlayer getPlayer(@NotNull String uuid);
    java.util.List<com.luatweaker.api.entity.IPlayer> getAllPlayers();

    @Nullable
    default com.luatweaker.api.entity.IPlayer getLocalPlayer() { return null; }

    @Nullable
    Object spawnEntity(@NotNull String entityId, double x, double y, double z);
}
