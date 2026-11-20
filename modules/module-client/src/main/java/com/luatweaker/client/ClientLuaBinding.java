package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

public class ClientLuaBinding {

    /** True on a client process (integrated or remote); false on a dedicated server. */
    private static boolean isClientRuntime() {
        return com.luatweaker.api.pal.Platform.isInitialized()
                && com.luatweaker.api.pal.Platform.getContent().isClient();
    }

    /**
     * True when this process can mutate the world: a dedicated server, or a client hosting an
     * integrated server. A client joined to a remote server reports false, so server-only Lua
     * branches are skipped instead of failing against a missing world.
     */
    private static boolean isServerRuntime() {
        if (!com.luatweaker.api.pal.Platform.isInitialized()) {
            // Engine-neutral test doubles keep the historical server-side defaults.
            return true;
        }
        com.luatweaker.api.pal.IPlatformContent content = com.luatweaker.api.pal.Platform.getContent();
        return content.isDedicatedServer() || content.hasLocalServer();
    }

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService) {
        registerBindings(engine, clientService, new KeyBindServiceImpl());
    }

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService, @NotNull IKeyBindService keyBindService) {
        registerBindings(engine, clientService, keyBindService, null);
    }

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService, @NotNull IKeyBindService keyBindService, com.luatweaker.api.client.IGuiService guiService) {
        ILuaTable globals = engine.getGlobalEnvironment();

        // Merge into the bootstrap-provided Client table (which defines OnKeyBindPressed)
        // instead of replacing it, so keybind Lua listeners keep working.
        ILuaValue existingClient = globals.rawget("Client");
        ILuaTable clientTable = (existingClient != null && existingClient.isTable())
                ? existingClient.asTable()
                : engine.createTable();
        clientTable.rawset("registerKeyBinding", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 5) {
                String id = args[off].asString();
                String displayName = args[off + 1].asString();
                String category = args[off + 2].asString();
                com.luatweaker.api.client.KeyNames.KeyRef ref =
                        com.luatweaker.api.client.KeyNames.parseKeyRef(args[off + 3], "defaultKey");
                String onPressPayload = args[off + 4].asString();
                String modifier = args.length - off >= 6
                        ? com.luatweaker.api.client.KeyNames.parseModifier(args[off + 5].asString())
                        : "none";
                keyBindService.registerKeyBind(id, displayName, category, ref.code(),
                        onPressPayload, ref.inputType(), modifier);
            } else if (args.length - off >= 4) {
                String id = args[off].asString();
                String category = args[off + 1].asString();
                com.luatweaker.api.client.KeyNames.KeyRef ref =
                        com.luatweaker.api.client.KeyNames.parseKeyRef(args[off + 2], "defaultKey");
                String onPressPayload = args[off + 3].asString();
                String modifier = args.length - off >= 5
                        ? com.luatweaker.api.client.KeyNames.parseModifier(args[off + 4].asString())
                        : "none";
                keyBindService.registerKeyBind(id, id, category, ref.code(),
                        onPressPayload, ref.inputType(), modifier);
            } else {
                throw new IllegalArgumentException(
                    "registerKeyBinding requires (id, category, defaultKey, onPressPayload)"
                    + " or (id, displayName, category, defaultKey, onPressPayload [, modifier]);"
                    + " the modifier ('none', 'shift', 'ctrl', 'alt') is only read"
                    + " in the full form, as the 6th argument");
            }
            return engine.nilValue();
        });
        clientTable.rawset("RegisterKeyBind", clientTable.rawget("registerKeyBinding"));
        clientTable.rawset("RegisterKeyBinding", clientTable.rawget("registerKeyBinding"));

        keyBindService.setKeyBindListener((id, payload) ->
                dispatchKeyBindSignal(engine, "OnKeyBindPressed", id, payload));
        keyBindService.setKeyBindReleaseListener((id, payload) ->
                dispatchKeyBindSignal(engine, "OnKeyBindReleased", id, payload));

        globals.rawset("Client", clientTable);
        engine.registerService("KeyBindService", keyBindService);

        ILuaValue uisValue = globals.rawget("UserInputService");
        if (uisValue != null && uisValue.isTable()) {
            ILuaTable uis = uisValue.asTable();
            // State comes from NeoForgeClientEventListener; on a dedicated server nothing
            // feeds it, so both queries simply stay false instead of the whole table
            // disappearing (one API surface on every distribution).
            uis.rawset("IsKeyDown", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 1) {
                    return engine.wrapBoolean(false);
                }
                return engine.wrapBoolean(clientService.isKeyDown(args[off].asInt()));
            });
            uis.rawset("IsMouseButtonDown", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 1) {
                    return engine.wrapBoolean(false);
                }
                return engine.wrapBoolean(clientService.isMouseButtonDown(args[off].asInt()));
            });
        }

        // Real implementations on every distribution: the services delegate through
        // Platform.getClientOrNull() and degrade to no-ops (with a debug line) where no
        // client exists. Dummy tables used to expose a smaller API on dedicated servers.
        LuaBinder.bind(engine, "Camera", new com.luatweaker.client.CameraServiceImpl(), com.luatweaker.api.client.ICameraService.class);
        StubCatalog.register("Camera", com.luatweaker.api.client.ICameraService.class);
        LuaBinder.bind(engine, "ClientEffects", new com.luatweaker.client.ClientEffectsServiceImpl(), com.luatweaker.api.client.IClientEffectsService.class);
        StubCatalog.register("ClientEffects", com.luatweaker.api.client.IClientEffectsService.class);
        LuaBinder.bind(engine, "TweenService", new com.luatweaker.client.TweenServiceImpl(engine), com.luatweaker.api.client.ITweenService.class);
        StubCatalog.register("TweenService", com.luatweaker.api.client.ITweenService.class);

        // 5. Roblox RunService — merge into the bootstrap table (keeps IsServer/IsClient)
        ILuaValue signalClass = globals.rawget("Signal");
        ILuaValue existingRun = globals.rawget("RunService");
        ILuaTable runService = (existingRun != null && existingRun.isTable())
                ? existingRun.asTable()
                : engine.createTable();
        if (signalClass != null && signalClass.isTable()) {
            ILuaValue newSignalFn = signalClass.asTable().rawget("new");
            if (newSignalFn != null && !newSignalFn.isNil()) {
                runService.rawset("RenderStepped", engine.callFunction(newSignalFn, signalClass));
                runService.rawset("Heartbeat", engine.callFunction(newSignalFn, signalClass));
                if (runService.rawget("Stepped") == null || runService.rawget("Stepped").isNil()) {
                    runService.rawset("Stepped", engine.callFunction(newSignalFn, signalClass));
                }
            }
        }
        // Runtime role, evaluated per call: a client joined to a remote server hosts no server,
        // while single-player hosts an integrated one. The bootstrap defaults (server=true,
        // client=false) were wrong for every client script.
        runService.rawset("IsClient", args -> engine.wrapBoolean(isClientRuntime()));
        runService.rawset("IsServer", args -> engine.wrapBoolean(isServerRuntime()));
        globals.rawset("RunService", runService);
        engine.registerService("RunService", runService);

        ILuaValue existingGuiValue = globals.rawget("GuiService");
        boolean hasExistingGui = existingGuiValue != null && existingGuiValue.isTable();
        // HUD rendering only exists on a client. Attaching OnRenderHUD on a dedicated
        // server would invite per-frame work that can never draw anything.
        boolean hasHudRenderer = !com.luatweaker.api.pal.Platform.isInitialized()
                || !com.luatweaker.api.pal.Platform.getContent().isDedicatedServer();

        if (guiService != null) {
            // 6. Roblox GuiService — method bindings auto-generated from IGuiService
            ILuaTable guiServiceTable = LuaBinder.bind(engine, "GuiService", guiService, com.luatweaker.api.client.IGuiService.class);
            StubCatalog.register("GuiService", com.luatweaker.api.client.IGuiService.class);
            if (hasHudRenderer && signalClass != null && signalClass.isTable()) {
                ILuaValue newSignalFn = signalClass.asTable().rawget("new");
                if (newSignalFn != null && !newSignalFn.isNil()) {
                    guiServiceTable.rawset("OnRenderHUD", engine.callFunction(newSignalFn, signalClass));
                }
            }

            // GetScreenSize returns a table {1, 2, Width, Height} — bind manually.
            guiServiceTable.rawset("GetScreenSize", args -> {
                int[] size = guiService.getScreenSize();
                ILuaTable result = engine.createTable();
                result.rawset(1, engine.wrapNumber(size[0]));
                result.rawset(2, engine.wrapNumber(size[1]));
                result.rawset("Width", engine.wrapNumber(size[0]));
                result.rawset("Height", engine.wrapNumber(size[1]));
                return result;
            });
            guiServiceTable.rawset("getScreenSize", guiServiceTable.rawget("GetScreenSize"));

            // MeasureText returns a table {1, 2, Width, Height}; the raw int[] from the
            // auto-binding would arrive as opaque userdata.
            guiServiceTable.rawset("MeasureText", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 1) {
                    return engine.nilValue();
                }
                int[] size = guiService.measureText(args[off].asString());
                ILuaTable result = engine.createTable();
                result.rawset(1, engine.wrapNumber(size[0]));
                result.rawset(2, engine.wrapNumber(size[1]));
                result.rawset("Width", engine.wrapNumber(size[0]));
                result.rawset("Height", engine.wrapNumber(size[1]));
                return result;
            });
            guiServiceTable.rawset("measureText", guiServiceTable.rawget("MeasureText"));

            guiServiceTable.rawset("CreateScreen", args -> {
                int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
                String sid = args.length > off ? args[off].asString() : "screen";
                String title = args.length > off + 1 ? args[off + 1].asString() : sid;
                ILuaTable screen = engine.createTable();
                screen.rawset("Id", engine.wrapString(sid));
                screen.rawset("Title", engine.wrapString(title));
                screen.rawset("Open", screenArgs -> {
                    guiService.openScreen(sid, title, false);
                    return engine.nilValue();
                });
                screen.rawset("open", screen.rawget("Open"));
                screen.rawset("Close", screenArgs -> {
                    guiService.closeScreen();
                    return engine.nilValue();
                });
                screen.rawset("close", screen.rawget("Close"));
                guiService.registerScreen(sid, screen);
                return screen;
            });
            guiServiceTable.rawset("createScreen", guiServiceTable.rawget("CreateScreen"));

            guiServiceTable.rawset("RegisterScreen", args -> {
                int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
                if (args.length > off + 1 && args[off + 1].isTable()) {
                    String sid = args[off].asString();
                    guiService.registerScreen(sid, args[off + 1].asTable());
                }
                return engine.nilValue();
            });
            guiServiceTable.rawset("registerScreen", guiServiceTable.rawget("RegisterScreen"));
            engine.registerService("GuiService", guiServiceTable);
        } else if (hasExistingGui) {
            // A second registration without an implementation must never replace a working
            // GuiService: that silently turned every draw call into a nil-returning no-op.
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "GuiService re-binding skipped: no IGuiService supplied, keeping the existing table");
        } else {
            // Only reachable when a caller supplies no implementation (tests, custom
            // embedders). LuaTweakerMod always supplies the NeoForge or headless service.
            com.luatweaker.api.log.LuaTweakerLog.get().debug(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "GuiService is not bound: no IGuiService was supplied");
        }

        engine.registerService("ClientService", clientService);
    }

    /**
     * Fires a keybind signal ({@code OnKeyBindPressed} / {@code OnKeyBindReleased})
     * directly, without building Lua source (string concatenation on payloads was
     * a self-inflicted source-injection path). A missing signal table is created
     * on demand so reloads that wipe the Client table cannot wedge listeners.
     */
    private static void dispatchKeyBindSignal(@NotNull ILuaEngine engine,
                                              @NotNull String signalName,
                                              @NotNull String id,
                                              @NotNull String payload) {
        com.luatweaker.api.vm.ILuaValue clientValue = engine.getGlobalEnvironment().rawget("Client");
        if (clientValue == null || !clientValue.isTable()) {
            return;
        }
        com.luatweaker.api.vm.ILuaTable clientTable = clientValue.asTable();
        com.luatweaker.api.vm.ILuaValue signal = clientTable.rawget(signalName);
        if (signal == null || signal.isNil() || !signal.isTable()) {
            signal = createSignal(engine);
            if (signal == null || signal.isNil() || !signal.isTable()) {
                return;
            }
            clientTable.rawset(signalName, signal);
        }
        // Methods live on the Signal class table (instances only carry
        // _listeners via metatable), so rawget on the instance always misses:
        // fall back to the class table like RunServiceDispatcher does.
        com.luatweaker.api.vm.ILuaValue signalClass =
                engine.getGlobalEnvironment().rawget("Signal");
        com.luatweaker.api.vm.ILuaTable signalClassTable =
                (signalClass != null && !signalClass.isNil() && signalClass.isTable())
                        ? signalClass.asTable() : null;
        com.luatweaker.api.vm.ILuaValue fire = signal.asTable().rawget("FireSync");
        if ((fire == null || fire.isNil() || !fire.isFunction()) && signalClassTable != null) {
            fire = signalClassTable.rawget("FireSync");
        }
        if ((fire == null || fire.isNil() || !fire.isFunction()) && signalClassTable != null) {
            fire = signalClassTable.rawget("Fire");
        }
        if (fire == null || fire.isNil() || !fire.isFunction()) {
            return;
        }
        try {
            engine.callFunction(fire, signal, engine.wrapString(id),
                    engine.wrapString(payload != null ? payload : ""));
        } catch (RuntimeException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "Failed to dispatch keybind Lua signal '" + id + "': " + e.getMessage()
            );
        }
    }

    /** Mirrors the bootstrap ({@code Signal.new()}); null when Signal is unavailable. */
    private static com.luatweaker.api.vm.ILuaValue createSignal(@NotNull ILuaEngine engine) {
        try {
            com.luatweaker.api.vm.ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
            if (signalClass == null || signalClass.isNil() || !signalClass.isTable()) {
                return null;
            }
            com.luatweaker.api.vm.ILuaValue newFn = signalClass.asTable().rawget("new");
            if (newFn == null || newFn.isNil() || !newFn.isFunction()) {
                return null;
            }
            return engine.callFunction(newFn, signalClass);
        } catch (RuntimeException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "Failed to create keybind signal table: " + e.getMessage()
            );
            return null;
        }
    }
}
