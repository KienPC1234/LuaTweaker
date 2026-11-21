package com.luatweaker.api.spawn;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-dimension custom spawn handler. The engine only provides the timing:
 * it calls your Lua handler periodically so you can spawn exactly what you
 * want, where you want. The rules themselves are your code.
 */
@LuaDoc(description = "Custom spawn handling: provide your own Lua spawn logic per dimension.")
public interface ISpawnRuleService {

    @LuaDoc(
        description = "Full-control spawn handler: function(dimensionId, players) -> { {entity=..., x=..., y=..., z=...}, ... }. Called every few ticks so you define all spawning yourself.",
        params = {LuaDocFragments.Common.DIMENSION_ID, "luaFunction: function"}
    )
    void registerHandler(@NotNull String dimensionId, @NotNull Object luaFunction);

    @LuaDoc(
        description = "Remove the spawn handler for a dimension.",
        params = {LuaDocFragments.Common.DIMENSION_ID}
    )
    void clearHandler(@NotNull String dimensionId);

    @LuaDoc(description = "Remove all spawn handlers.")
    void clearAll();

    @LuaDoc(
        description = "Registers a global spawn check hook: function(entity, dimensionId, x, y, z) -> boolean. Return false to deny the spawn.",
        params = {"luaFunction: function"}
    )
    default void onSpawnCheck(@NotNull Object luaFunction) {}

    @LuaDoc(
        description = "Add a declarative spawn rule. The vanilla natural spawner evaluates it at every "
            + "candidate position (light, sky light, Y, biome, block below, time, weather) before it "
            + "spawns, so vanilla handles caps, pack sizes, despawn and difficulty for you. "
            + "Table: { entity, category?, weight, minGroup, maxGroup, y = {min,max}, light = {min,max}, "
            + "skyLight = {min,max}, time = 'day'|'night', weather = 'clear'|'rain'|'thunder', "
            + "biomes = {...}, blockBelow = {...} (ids or '#tag'), solidGround = true, check = function(ctx) }. "
            + "Once a dimension has at least one rule, the biome's default spawn list is replaced by the "
            + "rules; return nothing from check (or true) to allow, false to veto.",
        params = {LuaDocFragments.Common.DIMENSION_ID, "rule: table - spawn rule"}
    )
    default void addRule(@NotNull String dimensionId, @NotNull ILuaValue rule) {}

    @LuaDoc(
        description = "Remove every declarative spawn rule of a dimension. The full-control handler is kept.",
        params = {LuaDocFragments.Common.DIMENSION_ID}
    )
    default void clearRules(@NotNull String dimensionId) {}

    @LuaDoc(
        description = "Returns the declarative spawn rules of a dimension as an array of tables (empty when none).",
        params = {LuaDocFragments.Common.DIMENSION_ID},
        returnType = "table"
    )
    @Nullable
    default Object getRules(@NotNull String dimensionId) {
        return null;
    }

    @LuaDoc(
        description = "Clears the global spawn check hook."
    )
    default void clearSpawnCheck() {}

    @NotNull
    default java.util.Set<String> getRegisteredDimensions() {
        return java.util.Set.of();
    }
}
