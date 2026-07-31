package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

public class ClientLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService) {
        registerBindings(engine, clientService, new KeyBindServiceImpl());
    }

    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService, @NotNull IKeyBindService keyBindService) {
        ILuaTable globals = engine.getGlobalEnvironment();

        boolean isDedicatedServer = com.luatweaker.api.pal.Platform.isInitialized() && com.luatweaker.api.pal.Platform.get().isDedicatedServer();

        ILuaTable clientTable = engine.createTable();
        clientTable.rawset("registerKeyBinding", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
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
            return null;
        });
        clientTable.rawset("RegisterKeyBind", clientTable.rawget("registerKeyBinding"));
        clientTable.rawset("RegisterKeyBinding", clientTable.rawget("registerKeyBinding"));
        globals.rawset("Client", clientTable);

        ILuaValue uisValue = globals.rawget("UserInputService");
        if (uisValue != null && uisValue.isTable()) {
            ILuaTable uis = uisValue.asTable();
            uis.rawset("IsKeyDown", args -> {
                if (isDedicatedServer) return engine.wrapBoolean(false);
                int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
                if (args.length - off >= 1) {
                    int key = args[off].asInt();
                    return engine.wrapBoolean(clientService.isKeyDown(key));
                }
                return engine.wrapBoolean(false);
            });
        }

        ILuaTable cameraTable = engine.createTable();
        cameraTable.rawset("Shake", args -> {
            if (isDedicatedServer) {
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Server Headless] Camera shake no-op on dedicated server");
                return null;
            }
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            double intensity = args.length - off >= 1 ? args[off].asDouble() : 1.0;
            double duration = args.length - off >= 2 ? args[off + 1].asDouble() : 0.5;
            com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Camera] Shake effect applied: intensity=" + intensity + ", duration=" + duration);
            return null;
        });
        globals.rawset("Camera", cameraTable);

        ILuaTable clientEffects = engine.createTable();
        clientEffects.rawset("SpawnParticle", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 4) {
                String particleId = args[off].asString();
                double x = args[off + 1].asDouble();
                double y = args[off + 2].asDouble();
                double z = args[off + 3].asDouble();
                double vx = args.length - off >= 5 ? args[off + 4].asDouble() : 0.0;
                double vy = args.length - off >= 6 ? args[off + 5].asDouble() : 0.0;
                double vz = args.length - off >= 7 ? args[off + 6].asDouble() : 0.0;
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Particle] Client Emitter: " + particleId + " at (" + x + "," + y + "," + z + ")");
            }
            return null;
        });
        clientEffects.rawset("PlaySound", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 1) {
                String soundId = args[off].asString();
                double volume = args.length - off >= 2 ? args[off + 1].asDouble() : 1.0;
                double pitch = args.length - off >= 3 ? args[off + 2].asDouble() : 1.0;
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Sound] Client PlaySound: " + soundId + " vol=" + volume + " pitch=" + pitch);
            }
            return null;
        });
        clientEffects.rawset("FlashScreen", args -> {
            if (isDedicatedServer) return null;
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 1) {
                String hex = args[off].asString();
                double duration = args.length - off >= 2 ? args[off + 1].asDouble() : 0.3;
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Overlay] Screen Flash: " + hex + " duration=" + duration);
            }
            return null;
        });

        // 4. Roblox TweenService
        ILuaTable tweenService = engine.createTable();
        tweenService.rawset("Create", args -> {
            ILuaTable tween = engine.createTable();
            tween.rawset("Play", a -> null);
            tween.rawset("Pause", a -> null);
            tween.rawset("Cancel", a -> null);
            return tween;
        });
        globals.rawset("TweenService", tweenService);
        engine.registerService("TweenService", tweenService);

        // 5. Roblox RunService
        ILuaValue signalClass = globals.rawget("Signal");
        ILuaTable runService = engine.createTable();
        if (signalClass != null && signalClass.isTable()) {
            ILuaValue newSignalFn = signalClass.asTable().rawget("new");
            if (newSignalFn != null && !newSignalFn.isNil()) {
                runService.rawset("RenderStepped", engine.callFunction(newSignalFn, signalClass));
                runService.rawset("Heartbeat", engine.callFunction(newSignalFn, signalClass));
            }
        }
        globals.rawset("RunService", runService);
        engine.registerService("RunService", runService);

        // 6. Roblox GuiService
        ILuaTable guiService = engine.createTable();
        guiService.rawset("ShowNotification", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 2) {
                String title = args[off].asString();
                String text = args[off + 1].asString();
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[GUI Notification] " + title + ": " + text);
            }
            return null;
        });
        globals.rawset("GuiService", guiService);
        engine.registerService("GuiService", guiService);

        engine.registerService("ClientService", clientService);
        engine.registerService("ClientEffects", clientEffects);
        engine.registerService("Camera", cameraTable);
    }
}
