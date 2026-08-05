package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper for resolving the owning LuaMod of the currently executing Lua code.
 *
 * <p>The engine's {@code mod} / {@code Mod} global is set by
 * {@link LuaModManager} to the table of the mod whose entrypoint is running.
 * NOTE: this global is only trustworthy while an entrypoint executes; runtime
 * permission decisions must NOT be based on it (see {@link #resolveCurrentModId}).</p>
 */
public final class LuaModContext {
    private LuaModContext() {}

    /**
     * Resolves the id of the Lua mod whose script is currently running from
     * the engine's global {@code mod} / {@code Mod} table.
     *
     * <p>WARNING: this is only trustworthy while a mod's entrypoint
     * ({@code main.lua} / {@code OnEnable}) is executing. After the load cycle
     * the global points at the last loaded mod, and scripts can overwrite the
     * table themselves - so it MUST NOT be used for runtime permission
     * decisions. Permission-gated runtime APIs resolve grants from the
     * Java-side mod registry (see module-update {@code WebServiceImpl}).</p>
     *
     * @return the mod id, or null when no mod context is active.
     */
    public static @Nullable String resolveCurrentModId(@NotNull ILuaEngine engine) {
        try {
            ILuaValue modVal = engine.getGlobalEnvironment().rawget("mod");
            if (modVal == null || modVal.isNil()) {
                modVal = engine.getGlobalEnvironment().rawget("Mod");
            }
            if (modVal != null && !modVal.isNil() && modVal.isTable()) {
                ILuaValue idVal = modVal.asTable().rawget("ID");
                if (idVal != null && !idVal.isNil()) {
                    return idVal.asString();
                }
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "LuaModContext: failed while resolving owning mod id: " + e.getMessage());
        }
        return null;
    }
}
