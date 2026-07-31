package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

public class ClientLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine, @NotNull ClientServiceImpl clientService) {
        ILuaTable globals = engine.getGlobalEnvironment();

        ILuaValue uisValue = globals.rawget("UserInputService");
        if (uisValue != null && uisValue.isTable()) {
            ILuaTable uis = uisValue.asTable();
            uis.rawset("IsKeyDown", args -> {
                if (args.length >= 2) {
                    int key = args[1].asInt();
                    return engine.wrapBoolean(clientService.isKeyDown(key));
                }
                return engine.wrapBoolean(false);
            });
        }

        ILuaTable cameraTable = engine.createTable();
        cameraTable.rawset("Shake", args -> {
            double intensity = args.length >= 2 ? args[1].asDouble() : 1.0;
            double duration = args.length >= 3 ? args[2].asDouble() : 0.5;
            com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Camera] Shake effect applied: intensity=" + intensity + ", duration=" + duration);
            return null;
        });
        globals.rawset("Camera", cameraTable);

        ILuaTable clientEffects = engine.createTable();
        clientEffects.rawset("SpawnParticle", args -> {
            if (args.length >= 5) {
                String particleId = args[1].asString();
                double x = args[2].asDouble();
                double y = args[3].asDouble();
                double z = args[4].asDouble();
                double vx = args.length >= 6 ? args[5].asDouble() : 0.0;
                double vy = args.length >= 7 ? args[6].asDouble() : 0.0;
                double vz = args.length >= 8 ? args[7].asDouble() : 0.0;
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Particle] Client Emitter: " + particleId + " at (" + x + "," + y + "," + z + ")");
            }
            return null;
        });
        clientEffects.rawset("PlaySound", args -> {
            if (args.length >= 2) {
                String soundId = args[1].asString();
                double volume = args.length >= 3 ? args[2].asDouble() : 1.0;
                double pitch = args.length >= 4 ? args[3].asDouble() : 1.0;
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Sound] Client PlaySound: " + soundId + " vol=" + volume + " pitch=" + pitch);
            }
            return null;
        });
        clientEffects.rawset("FlashScreen", args -> {
            if (args.length >= 2) {
                String hex = args[1].asString();
                double duration = args.length >= 3 ? args[2].asDouble() : 0.3;
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "[Overlay] Screen Flash: " + hex + " duration=" + duration);
            }
            return null;
        });

        engine.registerService("ClientService", clientService);
        engine.registerService("ClientEffects", clientEffects);
    }
}
