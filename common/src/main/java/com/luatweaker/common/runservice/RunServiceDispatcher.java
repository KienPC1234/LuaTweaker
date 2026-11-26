package com.luatweaker.common.runservice;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.signal.LuaSignalDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

/**
 * Fires a {@code RunService} signal synchronously on the calling thread. Both the client frame
 * hooks and the server tick use this so {@code Heartbeat}/{@code Stepped} behave the same on
 * either side, and listeners run before the frame/tick returns (no deferred task hop).
 */
public final class RunServiceDispatcher {

    private static final String RUN_SERVICE = "RunService";

    private RunServiceDispatcher() {
    }

    /**
     * Fires {@code RunService[signalName](deltaTime)}.
     *
     * @param engine     engine that owns the Lua listeners, may be {@code null}
     * @param signalName {@code "Heartbeat"}, {@code "Stepped"} or {@code "RenderStepped"}
     * @param deltaTime  seconds since the previous dispatch
     */
    public static void fire(@Nullable ILuaEngine engine, @NotNull String signalName, double deltaTime) {
        if (engine == null) {
            return;
        }
        try {
            LuaSignalDispatcher.fireNumber(engine, RUN_SERVICE, signalName, deltaTime);
        } catch (LuaException | IllegalStateException e) {
            // A broken listener (script error) or an engine that a reload already shut down
            // must never take down the tick/render loop.
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "RunService signal '" + signalName + "' dispatch failed: " + e.getMessage());
        }
    }
}
