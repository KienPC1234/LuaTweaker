package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.ai.IAIGoalService;
import com.luatweaker.api.vm.*;
import org.jetbrains.annotations.NotNull;

public class AIGoalLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine) {
        IAIGoalService service = new AIGoalServiceImpl(engine);
        ILuaTable table = engine.createTable();

        table.rawset("addGoal", args -> {
            if (args.length < 4) {
                throw new IllegalArgumentException("AIGoals:addGoal requires (entity, priority, goalTable)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int priority = args[2].asInt();
            ILuaTable goalTable = args[3].asTable();
            if (entity != null) {
                service.addGoal(entity, priority, goalTable);
            }
            return null;
        });

        table.rawset("addTargetGoal", args -> {
            if (args.length < 4) {
                throw new IllegalArgumentException("AIGoals:addTargetGoal requires (entity, priority, goalTable)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int priority = args[2].asInt();
            ILuaTable goalTable = args[3].asTable();
            if (entity != null) {
                service.addTargetGoal(entity, priority, goalTable);
            }
            return null;
        });

        table.rawset("removeGoal", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("AIGoals:removeGoal requires (entity, goalTable)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            ILuaTable goalTable = args[2].asTable();
            if (entity != null) {
                service.removeGoal(entity, goalTable);
            }
            return null;
        });

        table.rawset("clearGoals", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("AIGoals:clearGoals requires (entity)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            if (entity != null) {
                service.clearGoals(entity);
            }
            return null;
        });

        table.rawset("addSkillGoal", args -> {
            if (args.length < 6) {
                throw new IllegalArgumentException("AIGoals:addSkillGoal requires (entity, priority, skillName, cooldown, range, castCallback)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int priority = args[2].asInt();
            String skillName = args[3].asString();
            double cooldown = args[4].asDouble();
            double range = args[5].asDouble();
            Object callback = args.length >= 7 ? args[6] : null;
            if (entity != null) {
                service.addSkillGoal(entity, priority, skillName, cooldown, range, callback);
            }
            return null;
        });

        table.rawset("addDashGoal", args -> {
            if (args.length < 5) {
                throw new IllegalArgumentException("AIGoals:addDashGoal requires (entity, priority, cooldown, speed)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int priority = args[2].asInt();
            double cooldown = args[3].asDouble();
            double speed = args[4].asDouble();
            if (entity != null) {
                service.addDashGoal(entity, priority, cooldown, speed);
            }
            return null;
        });

        table.rawset("addNearestAttackableTargetGoal", args -> {
            if (args.length < 4) {
                throw new IllegalArgumentException("AIGoals:addNearestAttackableTargetGoal requires (entity, priority, targetType)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int priority = args[2].asInt();
            String targetType = args[3].asString();
            if (entity != null) {
                service.addNearestAttackableTargetGoal(entity, priority, targetType);
            }
            return null;
        });

        table.rawset("addHurtByTargetGoal", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("AIGoals:addHurtByTargetGoal requires (entity, priority)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int priority = args[2].asInt();
            if (entity != null) {
                service.addHurtByTargetGoal(entity, priority);
            }
            return null;
        });

        table.rawset("addMeleeAttackGoal", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("AIGoals:addMeleeAttackGoal requires (entity, priority, [speed], [pauseWhenIdle])");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int priority = args[2].asInt();
            double speed = args.length >= 4 ? args[3].asDouble() : 1.2;
            boolean pauseWhenIdle = args.length >= 5 && args[4].asBoolean();
            if (entity != null) {
                service.addMeleeAttackGoal(entity, priority, speed, pauseWhenIdle);
            }
            return null;
        });

        engine.registerService("AIGoals", table);
    }
}
