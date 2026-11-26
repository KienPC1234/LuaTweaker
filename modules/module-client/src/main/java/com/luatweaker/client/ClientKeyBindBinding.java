package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.client.KeyNames;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.signal.LuaSignalDispatcher;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the Lua {@code Client.registerKeyBinding} surface and dispatches the
 * {@code OnKeyBindPressed}/{@code OnKeyBindReleased} signals directly through {@link LuaSignalDispatcher}.
 */
final class ClientKeyBindBinding {

    private static final String CLIENT_TABLE = "Client";
    private static final String SIGNAL_NONE = "none";
    private static final String REGISTER_FN = "registerKeyBinding";
    private static final String REGISTER_ALIAS = "RegisterKeyBind";
    private static final String REGISTER_ALIAS_2 = "RegisterKeyBinding";
    private static final String PRESSED = "OnKeyBindPressed";
    private static final String RELEASED = "OnKeyBindReleased";

    private static final int FULL_KEYBIND_REQUIRED = 5;
    private static final int FULL_KEYBIND_WITH_MODIFIER = 6;
    private static final int COMPACT_KEYBIND_REQUIRED = 4;
    private static final int COMPACT_KEYBIND_WITH_MODIFIER = 5;

    private ClientKeyBindBinding() {}

    static void register(@NotNull ILuaEngine engine, @NotNull ILuaTable clientTable,
                         @NotNull IKeyBindService keyBindService) {
        ILuaValue registerFn = clientTable.rawget(REGISTER_FN);
        if (registerFn == null || !registerFn.isFunction()) {
            clientTable.rawset(REGISTER_FN, args -> {
                handleRegister(keyBindService, args);
                return engine.nilValue();
            });
            registerFn = clientTable.rawget(REGISTER_FN);
        }
        clientTable.rawset(REGISTER_ALIAS, registerFn);
        clientTable.rawset(REGISTER_ALIAS_2, registerFn);

        keyBindService.setKeyBindListener((id, payload) ->
                dispatchSignal(engine, PRESSED, id, payload));
        keyBindService.setKeyBindReleaseListener((id, payload) ->
                dispatchSignal(engine, RELEASED, id, payload));
    }

    private static void handleRegister(@NotNull IKeyBindService service,
                                       @NotNull ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        int count = args.length - off;
        if (count >= FULL_KEYBIND_REQUIRED) {
            registerFull(service, args, off);
        } else if (count >= COMPACT_KEYBIND_REQUIRED) {
            registerCompact(service, args, off);
        } else {
            throw new IllegalArgumentException(
                "registerKeyBinding requires (id, category, defaultKey, onPressPayload)"
                + " or (id, displayName, category, defaultKey, onPressPayload [, modifier]);"
                + " the modifier ('none', 'shift', 'ctrl', 'alt') is only read"
                + " in the full form, as the 6th argument");
        }
    }

    private static void registerFull(@NotNull IKeyBindService service, @NotNull ILuaValue[] args,
                                     int off) {
        String id = args[off].asString();
        String displayName = args[off + 1].asString();
        String category = args[off + 2].asString();
        KeyNames.KeyRef ref = KeyNames.parseKeyRef(args[off + 3], "defaultKey");
        String onPressPayload = args[off + 4].asString();
        String modifier = args.length - off >= FULL_KEYBIND_WITH_MODIFIER
                ? KeyNames.parseModifier(args[off + 5].asString())
                : SIGNAL_NONE;
        service.registerKeyBind(id, displayName, category, ref.code(), onPressPayload,
                ref.inputType(), modifier);
    }

    private static void registerCompact(@NotNull IKeyBindService service, @NotNull ILuaValue[] args,
                                        int off) {
        String id = args[off].asString();
        String category = args[off + 1].asString();
        KeyNames.KeyRef ref = KeyNames.parseKeyRef(args[off + 2], "defaultKey");
        String onPressPayload = args[off + 3].asString();
        String modifier = args.length - off >= COMPACT_KEYBIND_WITH_MODIFIER
                ? KeyNames.parseModifier(args[off + 4].asString())
                : SIGNAL_NONE;
        service.registerKeyBind(id, id, category, ref.code(), onPressPayload,
                ref.inputType(), modifier);
    }

    private static void dispatchSignal(@NotNull ILuaEngine engine, @NotNull String signalName,
                                       @NotNull String id, @NotNull String payload) {
        try {
            LuaSignalDispatcher.fireWithArgs(engine, CLIENT_TABLE, signalName, () -> new ILuaValue[] {
                    engine.wrapString(id),
                    engine.wrapString(payload != null ? payload : "")
            });
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed to dispatch keybind Lua signal '" + id + "': " + e.getMessage());
        }
    }
}
