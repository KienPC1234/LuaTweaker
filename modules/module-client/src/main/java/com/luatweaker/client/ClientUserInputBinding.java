package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

/**
 * Binds {@code UserInputService:IsKeyDown/IsMouseButtonDown}. State comes from
 * NeoForgeClientEventListener; on a dedicated server nothing feeds it, so both queries
 * stay false instead of the whole table disappearing.
 */
final class ClientUserInputBinding {

    private static final String USER_INPUT = "UserInputService";
    private static final int MIN_ARGS = 1;

    private ClientUserInputBinding() {}

    static void register(@NotNull ILuaEngine engine, @NotNull ILuaTable globals,
                         @NotNull ClientServiceImpl clientService) {
        ILuaValue uisValue = globals.rawget(USER_INPUT);
        if (uisValue == null || !uisValue.isTable()) {
            return;
        }
        ILuaTable uis = uisValue.asTable();
        uis.rawset("IsKeyDown", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ARGS) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(clientService.isKeyDown(args[off].asInt()));
        });
        uis.rawset("IsMouseButtonDown", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ARGS) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(clientService.isMouseButtonDown(args[off].asInt()));
        });
    }
}
