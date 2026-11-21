package com.luatweaker.platform.entity;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.pal.IPlatformEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NeoForgeEntityPlatform implements IPlatformEntity {
    @Override
    public void addCustomGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable, @NotNull ILuaEngine engine, boolean isTargetSelector) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addCustomGoal(entity, priority, goalTable, engine, isTargetSelector);
    }

    @Override
    public void removeCustomGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.removeCustomGoal(entity, goalTable);
    }

    @Override
    public void clearCustomGoals(@NotNull IEntity entity) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.clearCustomGoals(entity);
    }

    @Override
    public void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addMeleeAttackGoal(entity, priority, speed, pauseWhenMobIdle);
    }

    @Override
    public void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addHurtByTargetGoal(entity, priority);
    }

    @Override
    public void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addNearestAttackableTargetGoal(entity, priority, targetType);
    }

    @Override
    public void addFloatGoal(@NotNull IEntity entity, int priority) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addFloatGoal(entity, priority);
    }

    @Override
    public void addWanderGoal(@NotNull IEntity entity, int priority, double speed) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addWanderGoal(entity, priority, speed);
    }

    @Override
    public void addLookAtGoal(@NotNull IEntity entity, int priority, double distance, @NotNull String targetType) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addLookAtGoal(entity, priority, distance, targetType);
    }

    @Override
    public void addLookAroundGoal(@NotNull IEntity entity, int priority) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addLookAroundGoal(entity, priority);
    }

    @Override
    public void addPanicGoal(@NotNull IEntity entity, int priority, double speed) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addPanicGoal(entity, priority, speed);
    }

    @Override
    public void addAvoidGoal(@NotNull IEntity entity, int priority, @NotNull String targetType,
                             double distance, double walkSpeed, double sprintSpeed) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addAvoidGoal(entity, priority, targetType,
                distance, walkSpeed, sprintSpeed);
    }

    @Override
    public void addLeapGoal(@NotNull IEntity entity, int priority, double strength) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addLeapGoal(entity, priority, strength);
    }

    @Override
    public void addBreakDoorGoal(@NotNull IEntity entity, int priority, boolean hardOnly) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addBreakDoorGoal(entity, priority, hardOnly);
    }

    @Override
    public void addTemptGoal(@NotNull IEntity entity, int priority, double speed, @NotNull String itemId) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addTemptGoal(entity, priority, speed, itemId);
    }

    @Override
    public void addMoveToTargetGoal(@NotNull IEntity entity, int priority, double speed, double maxDistance) {
        com.luatweaker.platform.entity.ai.MobGoalHelper.addMoveToTargetGoal(entity, priority, speed, maxDistance);
    }

    @Override
    public java.util.@NotNull List<java.util.Map<String, Object>> listGoals(@NotNull IEntity entity) {
        return com.luatweaker.platform.entity.ai.MobGoalHelper.listGoals(entity);
    }

    @Override
    @Nullable
    public com.luatweaker.api.entity.IPlayer getPlayer(@NotNull String uuid) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                java.util.UUID id = java.util.UUID.fromString(uuid);
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    return new NeoForgePlayerWrapper(player);
                }
            } catch (Exception e) { com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Ignored exception: " + e.getMessage()); }
        }
        return null;
    }

    @Override
    public java.util.List<com.luatweaker.api.entity.IPlayer> getAllPlayers() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            java.util.List<com.luatweaker.api.entity.IPlayer> list = new java.util.ArrayList<>();
            for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
                list.add(new NeoForgePlayerWrapper(player));
            }
            return list;
        }
        return java.util.List.of();
    }

    @Override
    @Nullable
    public com.luatweaker.api.entity.IPlayer getLocalPlayer() {
        return com.luatweaker.api.pal.Platform.getClient().getLocalPlayer();
    }

    @Override
    @Nullable
    public Object spawnEntity(@NotNull String entityId, double x, double y, double z) {
        return com.luatweaker.platform.interaction.EntityInteractionHelper.spawnEntity(entityId, x, y, z);
    }
}
