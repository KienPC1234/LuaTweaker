package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;

public class NetworkLuaBinding {
    public static void registerBindings(ILuaEngine engine, IRocketNetworkService networkService) {
        ILuaTable network = engine.createTable();
        network.rawset("GetOrCreateRemoteEvent", args -> {
            if (args.length < 2) return engine.nilValue();
            String name = args[1].asString();
            return networkService.GetOrCreateRemoteEvent(name);
        });
        network.rawset("GetOrCreateRemoteFunction", args -> {
            if (args.length < 2) return engine.nilValue();
            String name = args[1].asString();
            return networkService.GetOrCreateRemoteFunction(name);
        });

        engine.registerService("NetworkService", network);
        engine.registerGlobal("NetworkService", network);
    }
}
