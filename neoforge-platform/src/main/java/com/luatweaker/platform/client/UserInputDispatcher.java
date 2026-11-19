package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

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
            ILuaValue signalValue = serviceValue.asTable().rawget(signalName);
            if (signalValue == null || !signalValue.isTable()) {
                return;
            }
            ILuaTable signal = signalValue.asTable();
            ILuaValue fireFn = signal.rawget("FireSync");
            if (fireFn == null || !fireFn.isFunction()) {
                ILuaValue signalClass = globals.rawget("Signal");
                if (signalClass != null && signalClass.isTable()) {
                    fireFn = signalClass.asTable().rawget("FireSync");
                }
            }
            if (fireFn != null && fireFn.isFunction()) {
                engine.callFunction(fireFn, signal,
                        engine.wrapString(inputType),
                        engine.wrapNumber(code),
                        engine.wrapNumber(action),
                        engine.wrapNumber(modifiers),
                        engine.wrapBoolean(gameProcessed));
            }
        } catch (RuntimeException e) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "UserInputService signal '" + signalName + "' dispatch failed: " + e.getMessage());
        }
    }
}
