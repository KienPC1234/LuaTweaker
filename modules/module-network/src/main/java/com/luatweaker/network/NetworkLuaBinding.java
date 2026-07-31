package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;

public class NetworkLuaBinding {
    public static void registerBindings(ILuaEngine engine, IRocketNetworkService networkService) {
        ILuaTable network = engine.createTable();
        network.rawset("GetOrCreateRemoteEvent", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 1) return engine.nilValue();
            String name = args[off].asString();
            return networkService.GetOrCreateRemoteEvent(name);
        });
        network.rawset("GetOrCreateRemoteFunction", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 1) return engine.nilValue();
            String name = args[off].asString();
            return networkService.GetOrCreateRemoteFunction(name);
        });

        engine.registerService("NetworkService", network);
        engine.registerService("Network", network);
        engine.registerService("NetworkServiceImpl", networkService);
        engine.registerGlobal("NetworkService", network);
        engine.registerGlobal("Network", network);
    }
}
