package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers the client-side Lua surface: keybinds, UserInputService, Camera/ClientEffects/
 * TweenService, RunService and GuiService. The per-domain table construction lives in the
 * {@code Client*Binding} collaborators so this class stays a small composition root.
 */
public final class ClientLuaBinding {

    private ClientLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine,
                                        @NotNull ClientServiceImpl clientService) {
        registerBindings(engine, clientService, new KeyBindServiceImpl());
    }

    public static void registerBindings(@NotNull ILuaEngine engine,
                                        @NotNull ClientServiceImpl clientService,
                                        @NotNull IKeyBindService keyBindService) {
        registerBindings(engine, clientService, keyBindService, null);
    }

    public static void registerBindings(@NotNull ILuaEngine engine,
                                        @NotNull ClientServiceImpl clientService,
                                        @NotNull IKeyBindService keyBindService,
                                        @Nullable IGuiService guiService) {
        ILuaTable globals = engine.getGlobalEnvironment();

        // Merge into the bootstrap-provided Client table (which defines OnKeyBindPressed)
        // instead of replacing it, so keybind Lua listeners keep working.
        ILuaValue existingClient = globals.rawget("Client");
        ILuaTable clientTable = existingClient != null && existingClient.isTable()
                ? existingClient.asTable() : engine.createTable();
        ClientKeyBindBinding.register(engine, clientTable, keyBindService);
        globals.rawset("Client", clientTable);
        engine.registerService("KeyBindService", keyBindService);

        ClientUserInputBinding.register(engine, globals, clientService);
        ClientServiceBindings.register(engine);

        ILuaValue signalClass = globals.rawget("Signal");
        ClientRunServiceBinding.register(engine, globals, signalClass);

        // HUD rendering only exists on a client; attaching OnRenderHUD on a dedicated server
        // would invite per-frame work that can never draw anything.
        boolean hasHudRenderer = !Platform.isInitialized()
                || !Platform.getContent().isDedicatedServer();
        ClientGuiBinding.register(engine, globals, guiService, hasHudRenderer, signalClass);

        engine.registerService("ClientService", clientService);
    }
}
