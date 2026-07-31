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
        Platform.get().addCustomGoal(entity, priority, goalTable, engine, false);
    }

    @Override
    public void addTargetGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable) {
        Platform.get().addCustomGoal(entity, priority, goalTable, engine, true);
    }

    @Override
    public void removeGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable) {
        Platform.get().removeCustomGoal(entity, goalTable);
    }

    @Override
    public void clearGoals(@NotNull IEntity entity) {
        Platform.get().clearCustomGoals(entity);
    }

    @Override
    public void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType) {
        Platform.get().addNearestAttackableTargetGoal(entity, priority, targetType);
    }

    @Override
    public void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {
        Platform.get().addHurtByTargetGoal(entity, priority);
    }

    @Override
    public void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {
        Platform.get().addMeleeAttackGoal(entity, priority, speed, pauseWhenMobIdle);
    }

    @Override
    public void addSkillGoal(@NotNull IEntity entity, int priority, @NotNull String skillName, double cooldownSeconds, double range, Object castCallback) {
        ILuaTable goalTable = engine.createTable();
        final long cooldownMs = (long) (cooldownSeconds * 1000.0);
        final long[] lastCastTime = new long[] { 0 };

        goalTable.rawset("canUse", args -> {
            IEntity target = entity.getTarget();
            if (target == null || !target.isAlive()) return engine.wrapBoolean(false);
            if (System.currentTimeMillis() - lastCastTime[0] < cooldownMs) return engine.wrapBoolean(false);
            double dist = entity.distanceTo(target);
            return engine.wrapBoolean(dist <= range);
        });

        goalTable.rawset("start", args -> {
            lastCastTime[0] = System.currentTimeMillis();
            IEntity target = entity.getTarget();
            if (castCallback != null) {
                if (castCallback instanceof com.luatweaker.api.vm.ILuaValue luaVal && luaVal.isFunction()) {
                    engine.callFunction(luaVal, target != null ? engine.wrapUserdata(target) : engine.nilValue());
                } else if (castCallback instanceof ILuaTable tbl) {
                    com.luatweaker.api.vm.ILuaValue fn = tbl.rawget("fn");
                    if (fn != null && fn.isFunction()) {
                        engine.callFunction(fn, target != null ? engine.wrapUserdata(target) : engine.nilValue());
                    }
                }
            }
            return null;
        });

        addGoal(entity, priority, goalTable);
    }

    @Override
    public void addDashGoal(@NotNull IEntity entity, int priority, double cooldownSeconds, double speed) {
        ILuaTable goalTable = engine.createTable();
        final long cooldownMs = (long) (cooldownSeconds * 1000.0);
        final long[] lastDashTime = new long[] { 0 };

        goalTable.rawset("canUse", args -> {
            IEntity target = entity.getTarget();
            if (target == null || !target.isAlive()) return engine.wrapBoolean(false);
            if (System.currentTimeMillis() - lastDashTime[0] < cooldownMs) return engine.wrapBoolean(false);
            double dist = entity.distanceTo(target);
            return engine.wrapBoolean(dist >= 2.0 && dist <= 18.0);
        });

        goalTable.rawset("start", args -> {
            lastDashTime[0] = System.currentTimeMillis();
            IEntity target = entity.getTarget();
            if (target != null) {
                double dx = target.getX() - entity.getX();
                double dy = target.getY() - entity.getY();
                double dz = target.getZ() - entity.getZ();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len > 0) {
                    entity.setMotion((dx / len) * speed, 0.3 * speed, (dz / len) * speed);
                }
            }
            return null;
        });

        addGoal(entity, priority, goalTable);
    }
}
