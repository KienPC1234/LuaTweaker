package com.luatweaker.common.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

/**
 * Fires {@code UserInputService.InputBegan/InputEnded} from the client input events.
 *
 * <p>Payload is the raw input primitive: {@code (inputType, code, action, modifiers,
 * gameProcessed)} where {@code inputType} is {@code "key"} or {@code "mouse"}, {@code code}
 * is the GLFW key/button id, {@code action} is GLFW's 1=press / 0=release / 2=repeat, and
 * {@code gameProcessed} is true while a GUI screen (chat, inventory, pause) has focus.
 * Scripts map the codes themselves; no high-level "Action" abstraction is imposed.</p>
 *
 * <p>Dispatch is synchronous: input handlers expect to mutate state (aim, camera, toggles)
 * in the same frame as the key event, and Signal:FireSync catches listener errors.</p>
 */
public final class UserInputDispatcher {

    public static final String TYPE_KEY = "key";
    public static final String TYPE_MOUSE = "mouse";
    private static final String SIGNAL_FIRE_SYNC = "FireSync";

    private UserInputDispatcher() {
    }

    public static void fire(@NotNull ILuaEngine engine, @NotNull String signalName,
                            @NotNull String inputType, int code, int action, int modifiers) {
        fire(engine, signalName, inputType, code, action, modifiers, false);
    }

    public static void fire(@NotNull ILuaEngine engine, @NotNull String signalName,
                            @NotNull String inputType, int code, int action, int modifiers,
                            boolean gameProcessed) {
        try {
            ILuaTable globals = engine.getGlobalEnvironment();
            ILuaValue serviceValue = globals.rawget("UserInputService");
            if (serviceValue == null || !serviceValue.isTable()) {
                return;
            }
            ILuaTable signal = signalTable(serviceValue.asTable(), signalName);
            if (signal == null) {
                return;
            }
            ILuaValue fireFn = fireFunction(globals, signal);
            if (fireFn == null) {
                return;
            }
            engine.callFunction(fireFn, signal,
                    engine.wrapString(inputType),
                    engine.wrapNumber(code),
                    engine.wrapNumber(action),
                    engine.wrapNumber(modifiers),
                    engine.wrapBoolean(gameProcessed));
        } catch (LuaException | IllegalStateException e) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "UserInputService signal '" + signalName + "' dispatch failed: " + e.getMessage());
        }
    }

    private static @Nullable ILuaTable signalTable(@NotNull ILuaTable service, @NotNull String signalName) {
        ILuaValue signalValue = service.rawget(signalName);
        return signalValue != null && signalValue.isTable() ? signalValue.asTable() : null;
    }

    /**
     * Resolves {@code FireSync}, preferring the signal's own method and falling back to the
     * shared {@code Signal} class table (signals created before the bootstrap upgrade).
     */
    private static @Nullable ILuaValue fireFunction(@NotNull ILuaTable globals, @NotNull ILuaTable signal) {
        ILuaValue fireFn = signal.rawget(SIGNAL_FIRE_SYNC);
        if (fireFn != null && fireFn.isFunction()) {
            return fireFn;
        }
        ILuaValue signalClass = globals.rawget("Signal");
        if (signalClass == null || !signalClass.isTable()) {
            return null;
        }
        ILuaValue classFire = signalClass.asTable().rawget(SIGNAL_FIRE_SYNC);
        return classFire != null && classFire.isFunction() ? classFire : null;
    }
}
