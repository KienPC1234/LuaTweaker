package com.luatweaker.common.runservice;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

/**
 * Fires a {@code RunService} signal synchronously on the calling thread. Both the client frame
 * hooks and the server tick use this so {@code Heartbeat}/{@code Stepped} behave the same on
 * either side, and listeners run before the frame/tick returns (no deferred task hop).
 */
public final class RunServiceDispatcher {

    private static final String SIGNAL_FIRE_SYNC = "FireSync";
    private static final String SIGNAL_CLASS_NAME = "Signal";

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
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaTable signal = signalTable(globals, signalName);
            if (signal == null) {
                return;
            }
            // Hot-tick fast path: when nothing connected to this signal, skip the
            // wrapNumber + Lua pcall entirely. _listeners is maintained by
            // Signal:Connect/Disconnect in luatweaker_bootstrap.lua.
            if (!hasListeners(signal)) {
                return;
            }
            ILuaValue fireFn = fireFunction(globals, signal);
            if (fireFn != null) {
                engine.callFunction(fireFn, signal, engine.wrapNumber(deltaTime));
            }
        } catch (LuaException | IllegalStateException e) {
            // A broken listener (script error) or an engine that a reload already shut down
            // must never take down the tick/render loop.
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "RunService signal '" + signalName + "' dispatch failed: " + e.getMessage());
        }
    }

    private static @Nullable ILuaTable signalTable(@NotNull ILuaTable globals, @NotNull String signalName) {
        ILuaValue runServiceValue = globals.rawget("RunService");
        if (runServiceValue == null || !runServiceValue.isTable()) {
            return null;
        }
        ILuaValue signalValue = runServiceValue.asTable().rawget(signalName);
        return signalValue != null && signalValue.isTable() ? signalValue.asTable() : null;
    }

    private static boolean hasListeners(@NotNull ILuaTable signal) {
        ILuaValue listeners = signal.rawget("_listeners");
        return listeners != null && listeners.isTable() && listeners.asTable().length() > 0;
    }

    /** Resolves {@code FireSync}, preferring the signal's own method over the class table. */
    private static @Nullable ILuaValue fireFunction(@NotNull ILuaTable globals, @NotNull ILuaTable signal) {
        ILuaValue fireFn = signal.rawget(SIGNAL_FIRE_SYNC);
        if (fireFn != null && fireFn.isFunction()) {
            return fireFn;
        }
        ILuaValue signalClass = globals.rawget(SIGNAL_CLASS_NAME);
        if (signalClass == null || !signalClass.isTable()) {
            return null;
        }
        ILuaValue classFire = signalClass.asTable().rawget(SIGNAL_FIRE_SYNC);
        return classFire != null && classFire.isFunction() ? classFire : null;
    }
}
