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
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 3) {
                throw new IllegalArgumentException("AIGoals:addGoal requires (entity, priority, goalTable)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            int priority = args[off + 1].asInt();
            ILuaTable goalTable = args[off + 2].asTable();
            if (entity != null) {
                service.addGoal(entity, priority, goalTable);
            }
            return null;
        });

        table.rawset("addTargetGoal", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 3) {
                throw new IllegalArgumentException("AIGoals:addTargetGoal requires (entity, priority, goalTable)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            int priority = args[off + 1].asInt();
            ILuaTable goalTable = args[off + 2].asTable();
            if (entity != null) {
                service.addTargetGoal(entity, priority, goalTable);
            }
            return null;
        });

        table.rawset("removeGoal", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 2) {
                throw new IllegalArgumentException("AIGoals:removeGoal requires (entity, goalTable)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            ILuaTable goalTable = args[off + 1].asTable();
            if (entity != null) {
                service.removeGoal(entity, goalTable);
            }
            return null;
        });

        table.rawset("clearGoals", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 1) {
                throw new IllegalArgumentException("AIGoals:clearGoals requires (entity)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            if (entity != null) {
                service.clearGoals(entity);
            }
            return null;
        });

        table.rawset("addSkillGoal", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 5) {
                throw new IllegalArgumentException("AIGoals:addSkillGoal requires (entity, priority, skillName, cooldown, range, [castCallback])");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            int priority = args[off + 1].asInt();
            String skillName = args[off + 2].asString();
            double cooldown = args[off + 3].asDouble();
            double range = args[off + 4].asDouble();
            Object callback = args.length - off >= 6 ? args[off + 5] : null;
            if (entity != null) {
                service.addSkillGoal(entity, priority, skillName, cooldown, range, callback);
            }
            return null;
        });

        table.rawset("addDashGoal", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 4) {
                throw new IllegalArgumentException("AIGoals:addDashGoal requires (entity, priority, cooldown, speed)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            int priority = args[off + 1].asInt();
            double cooldown = args[off + 2].asDouble();
            double speed = args[off + 3].asDouble();
            if (entity != null) {
                service.addDashGoal(entity, priority, cooldown, speed);
            }
            return null;
        });

        table.rawset("addNearestAttackableTargetGoal", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 3) {
                throw new IllegalArgumentException("AIGoals:addNearestAttackableTargetGoal requires (entity, priority, targetType)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            int priority = args[off + 1].asInt();
            String targetType = args[off + 2].asString();
            if (entity != null) {
                service.addNearestAttackableTargetGoal(entity, priority, targetType);
            }
            return null;
        });

        table.rawset("addHurtByTargetGoal", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 2) {
                throw new IllegalArgumentException("AIGoals:addHurtByTargetGoal requires (entity, priority)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            int priority = args[off + 1].asInt();
            if (entity != null) {
                service.addHurtByTargetGoal(entity, priority);
            }
            return null;
        });

        table.rawset("addMeleeAttackGoal", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 2) {
                throw new IllegalArgumentException("AIGoals:addMeleeAttackGoal requires (entity, priority, [speed], [pauseWhenIdle])");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[off]);
            int priority = args[off + 1].asInt();
            double speed = args.length - off >= 3 ? args[off + 2].asDouble() : 1.2;
            boolean pauseWhenIdle = args.length - off >= 4 && args[off + 3].asBoolean();
            if (entity != null) {
                service.addMeleeAttackGoal(entity, priority, speed, pauseWhenIdle);
            }
            return null;
        });

        engine.registerService("AIGoals", table);
        engine.registerGlobal("AIGoals", table);
    }
}
