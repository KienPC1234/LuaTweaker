package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

public class ClientLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService) {
        registerBindings(engine, clientService, new KeyBindServiceImpl());
    }

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService, @NotNull IKeyBindService keyBindService) {
        registerBindings(engine, clientService, keyBindService, null);
    }

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService, @NotNull IKeyBindService keyBindService, com.luatweaker.api.client.IGuiService guiService) {
        ILuaTable globals = engine.getGlobalEnvironment();

        boolean isDedicatedServer = com.luatweaker.api.pal.Platform.isInitialized() && com.luatweaker.api.pal.Platform.getContent().isDedicatedServer();

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
                int defaultKey = args[off + 3].asInt();
                String onPressPayload = args[off + 4].asString();
                keyBindService.registerKeyBind(id, displayName, category, defaultKey, onPressPayload);
            } else if (args.length - off >= 4) {
                String id = args[off].asString();
                String category = args[off + 1].asString();
                int defaultKey = args[off + 2].asInt();
                String onPressPayload = args[off + 3].asString();
                keyBindService.registerKeyBind(id, id, category, defaultKey, onPressPayload);
            }
            return engine.nilValue();
        });
        clientTable.rawset("RegisterKeyBind", clientTable.rawget("registerKeyBinding"));
        clientTable.rawset("RegisterKeyBinding", clientTable.rawget("registerKeyBinding"));

        keyBindService.setKeyBindListener((id, payload) -> {
            // Call the signal directly. Building Lua source with String.format broke on quotes
            // in the payload and was a self-inflicted source-injection path.
            com.luatweaker.api.vm.ILuaValue clientValue = engine.getGlobalEnvironment().rawget("Client");
            if (clientValue == null || !clientValue.isTable()) {
                return;
            }
            com.luatweaker.api.vm.ILuaValue signal = clientValue.asTable().rawget("OnKeyBindPressed");
            if (signal == null || !signal.isTable()) {
                return;
            }
            com.luatweaker.api.vm.ILuaValue fire = signal.asTable().rawget("FireSync");
            if (fire == null || !fire.isFunction()) {
                fire = signal.asTable().rawget("Fire");
            }
            if (fire == null || !fire.isFunction()) {
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
        });

        globals.rawset("Client", clientTable);
        engine.registerService("KeyBindService", keyBindService);

        ILuaValue uisValue = globals.rawget("UserInputService");
        if (uisValue != null && uisValue.isTable()) {
            ILuaTable uis = uisValue.asTable();
            uis.rawset("IsKeyDown", args -> {
                if (isDedicatedServer) return engine.wrapBoolean(false);
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off >= 1) {
                    int key = args[off].asInt();
                    return engine.wrapBoolean(clientService.isKeyDown(key));
                }
                return engine.wrapBoolean(false);
            });
        }

        if (isDedicatedServer) {
            // Bind dummy services for dedicated server that do nothing
            ILuaTable dummyCamera = engine.createTable();
            dummyCamera.rawset("Shake", args -> null);
            globals.rawset("Camera", dummyCamera);
            engine.registerService("Camera", dummyCamera);

            ILuaTable dummyEffects = engine.createTable();
            dummyEffects.rawset("SpawnParticle", args -> null);
            dummyEffects.rawset("PlaySound", args -> null);
            dummyEffects.rawset("FlashScreen", args -> null);
            globals.rawset("ClientEffects", dummyEffects);
            engine.registerService("ClientEffects", dummyEffects);
            
            ILuaTable dummyTween = engine.createTable();
            dummyTween.rawset("Create", args -> null);
            globals.rawset("TweenService", dummyTween);
            engine.registerService("TweenService", dummyTween);
        } else {
            LuaBinder.bind(engine, "Camera", new com.luatweaker.client.CameraServiceImpl(), com.luatweaker.api.client.ICameraService.class);
            StubCatalog.register("Camera", com.luatweaker.api.client.ICameraService.class);
            LuaBinder.bind(engine, "ClientEffects", new com.luatweaker.client.ClientEffectsServiceImpl(), com.luatweaker.api.client.IClientEffectsService.class);
            StubCatalog.register("ClientEffects", com.luatweaker.api.client.IClientEffectsService.class);
            LuaBinder.bind(engine, "TweenService", new com.luatweaker.client.TweenServiceImpl(engine), com.luatweaker.api.client.ITweenService.class);
            StubCatalog.register("TweenService", com.luatweaker.api.client.ITweenService.class);
        }

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
            }
        }
        globals.rawset("RunService", runService);
        engine.registerService("RunService", runService);

        if (!isDedicatedServer) {
            // 6. Roblox GuiService — method bindings auto-generated from IGuiService
            ILuaTable guiServiceTable = LuaBinder.bind(engine, "GuiService", guiService, com.luatweaker.api.client.IGuiService.class);
            StubCatalog.register("GuiService", com.luatweaker.api.client.IGuiService.class);
            if (signalClass != null && signalClass.isTable()) {
                ILuaValue newSignalFn = signalClass.asTable().rawget("new");
                if (newSignalFn != null && !newSignalFn.isNil()) {
                    guiServiceTable.rawset("OnRenderHUD", engine.callFunction(newSignalFn, signalClass));
                }
            }

            // GetScreenSize returns a table {1, 2, Width, Height} — bind manually.
            guiServiceTable.rawset("GetScreenSize", args -> {
                if (guiService != null) {
                    int[] size = guiService.getScreenSize();
                    ILuaTable result = engine.createTable();
                    result.rawset(1, engine.wrapNumber(size[0]));
                    result.rawset(2, engine.wrapNumber(size[1]));
                    result.rawset("Width", engine.wrapNumber(size[0]));
                    result.rawset("Height", engine.wrapNumber(size[1]));
                    return result;
                }
                return engine.nilValue();
            });
            guiServiceTable.rawset("getScreenSize", guiServiceTable.rawget("GetScreenSize"));
            engine.registerService("GuiService", guiServiceTable);

            engine.registerService("ClientService", clientService);
        } else {
            // Ensure GuiService is bound as a dummy
            ILuaTable dummyGui = engine.createTable();
            dummyGui.rawset("GetScreenSize", args -> engine.createTable());
            dummyGui.rawset("getScreenSize", dummyGui.rawget("GetScreenSize"));
            globals.rawset("GuiService", dummyGui);
            engine.registerService("GuiService", dummyGui);
            
            ILuaTable dummyClientService = engine.createTable();
            engine.registerService("ClientService", dummyClientService);
        }
    }
}
