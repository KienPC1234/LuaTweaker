package com.luatweaker.api.spawn;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;

/**
 * Per-dimension custom spawn handler. The engine only provides the timing:
 * it calls your Lua handler periodically so you can spawn exactly what you
 * want, where you want. The rules themselves are your code.
 */
@LuaDoc(description = "Custom spawn handling: provide your own Lua spawn logic per dimension.")
public interface ISpawnRuleService {

    @LuaDoc(
        description = "Full-control spawn handler: function(dimensionId, players) -> { {entity=..., x=..., y=..., z=...}, ... }. Called every few ticks so you define all spawning yourself.",
        params = {"dimensionId: string", "luaFunction: function"},
        returnType = "void"
    )
    void registerHandler(@NotNull String dimensionId, @NotNull Object luaFunction);

    @LuaDoc(
        description = "Remove the spawn handler for a dimension.",
        params = {"dimensionId: string"},
        returnType = "void"
    )
    void clearHandler(@NotNull String dimensionId);

    @LuaDoc(description = "Remove all spawn handlers.", returnType = "void")
    void clearAll();
}
