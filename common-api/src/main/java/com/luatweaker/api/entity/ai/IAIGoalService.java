package com.luatweaker.api.entity.ai;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

@LuaDoc(description = "Service for managing custom Entity AI Goals from Lua.")
public interface IAIGoalService {
    @LuaDoc(description = "Adds a custom behavior goal to the entity's goal selector.", params = {"entity: table", "priority: integer", "goalTable: table"})
    void addGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable);

    @LuaDoc(description = "Adds a custom target selection goal to the entity's target selector.", params = {"entity: table", "priority: integer", "goalTable: table"})
    void addTargetGoal(@NotNull IEntity entity, int priority, @NotNull ILuaTable goalTable);

    @LuaDoc(description = "Removes a specific custom goal from the entity.", params = {"entity: table", "goalTable: table"})
    void removeGoal(@NotNull IEntity entity, @NotNull ILuaTable goalTable);

    @LuaDoc(description = "Clears all custom goals registered on the entity.", params = {"entity: table"})
    void clearGoals(@NotNull IEntity entity);

    default void addSkillGoal(@NotNull IEntity entity, int priority, @NotNull String skillName, double cooldownSeconds, double range, @NotNull Object castCallback) {}
    default void addDashGoal(@NotNull IEntity entity, int priority, double cooldownSeconds, double speed) {}

    @LuaDoc(description = "Adds a target selection goal targeting nearest players, monsters, or living entities.", params = {"entity: table", "priority: integer", "targetType: string ('player' | 'monster' | 'animal' | 'living')"})
    default void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType) {}

    @LuaDoc(description = "Adds a revenge target selection goal when entity is damaged.", params = {"entity: table", "priority: integer"})
    default void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {}

    @LuaDoc(description = "Adds a standard melee attack goal.", params = {"entity: table", "priority: integer", "[speed: number]", "[pauseWhenMobIdle: boolean]"})
    default void addMeleeAttackGoal(@NotNull IEntity entity, int priority, double speed, boolean pauseWhenMobIdle) {}
}
