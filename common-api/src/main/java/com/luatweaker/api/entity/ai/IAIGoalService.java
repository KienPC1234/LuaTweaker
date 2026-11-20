package com.luatweaker.api.entity.ai;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;
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

    @LuaDoc(description = "Adds a target selection goal targeting nearest players, monsters, or living entities.", params = {"entity: table", "priority: integer", "targetType: string ('player' | 'monster' | 'animal' | 'living')"})
    default void addNearestAttackableTargetGoal(@NotNull IEntity entity, int priority, @NotNull String targetType) {}

    @LuaDoc(description = "Adds a revenge target selection goal when entity is damaged.", params = {"entity: table", "priority: integer"})
    default void addHurtByTargetGoal(@NotNull IEntity entity, int priority) {}

    @LuaDoc(description = "Adds a standard melee attack goal.", params = {"entity: table", "priority: integer", "[speed: number]", "[pauseWhenMobIdle: boolean]"})
    default void addMeleeAttackGoal(@NotNull IEntity entity, int priority, @LuaDefault("1.2") double speed, @LuaDefault("false") boolean pauseWhenMobIdle) {}

    @LuaDoc(description = "Adds a float-in-water goal (entity swims instead of sinking).", params = {"entity: table", "priority: integer"})
    default void addFloatGoal(@NotNull IEntity entity, int priority) {}

    @LuaDoc(description = "Adds idle wandering. Requires PathfinderMob (all custom mobs).", params = {"entity: table", "priority: integer", "[speed: number]"})
    default void addWanderGoal(@NotNull IEntity entity, int priority, @LuaDefault("1.0") double speed) {}

    @LuaDoc(description = "Adds a look-at-nearby-entity goal.", params = {"entity: table", "priority: integer", "[distance: number]", "[targetType: string]"})
    default void addLookAtGoal(@NotNull IEntity entity, int priority, @LuaDefault("8.0") double distance, @LuaDefault("player") @NotNull String targetType) {}

    @LuaDoc(description = "Adds random head-turning when idle.", params = {"entity: table", "priority: integer"})
    default void addLookAroundGoal(@NotNull IEntity entity, int priority) {}

    @LuaDoc(description = "Adds flee-when-hurt behavior.", params = {"entity: table", "priority: integer", "[speed: number]"})
    default void addPanicGoal(@NotNull IEntity entity, int priority, @LuaDefault("1.25") double speed) {}

    @LuaDoc(description = "Adds keep-away-from-target behavior.", params = {"entity: table", "priority: integer", "[targetType: string]", "[distance: number]", "[walkSpeed: number]", "[sprintSpeed: number]"})
    default void addAvoidGoal(@NotNull IEntity entity, int priority, @LuaDefault("player") @NotNull String targetType,
                              @LuaDefault("6.0") double distance, @LuaDefault("1.0") double walkSpeed,
                              @LuaDefault("1.2") double sprintSpeed) {}

    @LuaDoc(description = "Adds leap-at-attack-target behavior.", params = {"entity: table", "priority: integer", "[strength: number]"})
    default void addLeapGoal(@NotNull IEntity entity, int priority, @LuaDefault("0.4") double strength) {}

    @LuaDoc(description = "Adds door-breaking (zombie-style).", params = {"entity: table", "priority: integer", "[hardOnly: boolean]"})
    default void addBreakDoorGoal(@NotNull IEntity entity, int priority, @LuaDefault("true") boolean hardOnly) {}

    @LuaDoc(description = "Adds follow-item (tempt) behavior for the given item id.", params = {"entity: table", "priority: integer", "[speed: number]", "[itemId: string]"})
    default void addTemptGoal(@NotNull IEntity entity, int priority, @LuaDefault("1.0") double speed, @LuaDefault("minecraft:wheat") @NotNull String itemId) {}

    @LuaDoc(description = "Adds move-toward-attack-target behavior (archers/creepers style approach).", params = {"entity: table", "priority: integer", "[speed: number]", "[maxDistance: number]"})
    default void addMoveToTargetGoal(@NotNull IEntity entity, int priority, @LuaDefault("1.0") double speed, @LuaDefault("16.0") double maxDistance) {}

    @LuaDoc(
        description = "Lists the entity's current goals for introspection: array of {side, priority, goal, running}.",
        params = {"entity: table"},
        returnType = "table"
    )
    default java.util.@NotNull List<java.util.Map<String, Object>> listGoals(@NotNull IEntity entity) {
        return java.util.List.of();
    }
}
