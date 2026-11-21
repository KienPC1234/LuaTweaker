package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.luava.runtime.LuaException;

import java.util.Map;

/**
 * Fires {@code OnEnable}/{@code OnDisable} listener hooks around the load cycle.
 */
final class ModLifecycleHooks {
    private ModLifecycleHooks() {}

    /** Triggers a loaded mod's {@code OnEnable} and records any failure. */
    static void triggerOnEnable(@NotNull ILuaEngine engine, @NotNull ILuaTable modTable,
            @NotNull String modId, @NotNull Map<String, String> loadErrors) {
        ILuaValue onEnableVal = modTable.rawget("OnEnable");
        if (onEnableVal == null || onEnableVal.isNil()) {
            onEnableVal = modTable.rawget("onEnable");
        }
        if (onEnableVal == null || !onEnableVal.isFunction()) {
            return;
        }
        engine.callFunction(onEnableVal);
        String enableError = engine.getAndClearLastExecutionError();
        if (enableError != null) {
            loadErrors.put(modId, enableError);
        }
    }

    /**
     * Fires {@code OnDisable} for every loaded mod on the engine that loaded it. Callers must pass
     * the owning engine: firing old-VM closures on a new engine corrupts both VMs. The loaded map
     * is cleared afterwards so a later load call does not fire the hooks twice.
     */
    static void fireModTeardownHooks(@NotNull ILuaEngine engine, @NotNull Map<String, LuaMod> loadedMods) {
        for (Map.Entry<String, LuaMod> entry : loadedMods.entrySet()) {
            String modId = entry.getKey();
            LuaMod mod = entry.getValue();
            ILuaTable modTable = mod.getModTable();
            if (modTable == null) {
                continue;
            }
            ILuaValue onDisable = modTable.rawget("OnDisable");
            if (onDisable == null || onDisable.isNil()) {
                onDisable = modTable.rawget("onDisable");
            }
            if (onDisable != null && onDisable.isFunction()) {
                try {
                    LuaTweakerLog.get().info(LogStage.SYSTEM,
                            "[LuaMod][" + modId + "] Firing OnDisable teardown hook...");
                    engine.callFunction(onDisable);
                } catch (LuaException | IllegalStateException e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM,
                            "[LuaMod][" + modId + "] Error in OnDisable hook: " + e.getMessage());
                }
            }
        }
        loadedMods.clear();
    }
}
