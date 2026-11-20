package com.luatweaker.platform.runservice;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fires a {@code RunService} signal synchronously on the calling thread. Both the client frame
 * hooks and the server tick use this so {@code Heartbeat}/{@code Stepped} behave the same on
 * either side, and listeners run before the frame/tick returns (no deferred task hop).
 */
public final class RunServiceDispatcher {

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
            ILuaValue runServiceValue = globals.rawget("RunService");
            if (runServiceValue == null || !runServiceValue.isTable()) {
                return;
            }
            ILuaValue signalValue = runServiceValue.asTable().rawget(signalName);
            if (signalValue == null || !signalValue.isTable()) {
                return;
            }
            ILuaTable signal = signalValue.asTable();
            // Hot-tick fast path: when nothing connected to this signal, skip the
            // wrapNumber + Lua pcall entirely. _listeners is maintained by
            // Signal:Connect/Disconnect in luatweaker_bootstrap.lua.
            ILuaValue listeners = signal.rawget("_listeners");
            if (listeners == null || !listeners.isTable()
                    || listeners.asTable().length() == 0) {
                return;
            }
            ILuaValue fireFn = signal.rawget("FireSync");
            if (fireFn == null || !fireFn.isFunction()) {
                ILuaValue signalClass = globals.rawget("Signal");
                if (signalClass != null && signalClass.isTable()) {
                    fireFn = signalClass.asTable().rawget("FireSync");
                }
            }
            if (fireFn != null && fireFn.isFunction()) {
                engine.callFunction(fireFn, signal, engine.wrapNumber(deltaTime));
            }
        } catch (RuntimeException e) {
            // A broken listener must never take down the tick/render loop.
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "RunService signal '" + signalName + "' dispatch failed: " + e.getMessage());
        }
    }
}
