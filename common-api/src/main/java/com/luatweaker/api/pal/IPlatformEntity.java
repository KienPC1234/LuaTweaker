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

    @Nullable
    com.luatweaker.api.entity.IPlayer getPlayer(@NotNull String uuid);
    java.util.List<com.luatweaker.api.entity.IPlayer> getAllPlayers();

    @Nullable
    Object spawnEntity(@NotNull String entityId, double x, double y, double z);
}
