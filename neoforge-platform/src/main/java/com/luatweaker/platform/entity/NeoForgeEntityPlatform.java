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
    @Nullable
    public com.luatweaker.api.entity.IPlayer getPlayer(@NotNull String uuid) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            try {
                java.util.UUID id = java.util.UUID.fromString(uuid);
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    return new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player);
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
                list.add(new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player));
            }
            return list;
        }
        return java.util.List.of();
    }

    @Override
    @Nullable
    public Object spawnEntity(@NotNull String entityId, double x, double y, double z) {
        return com.luatweaker.platform.interaction.EntityInteractionHelper.spawnEntity(entityId, x, y, z);
    }
}
