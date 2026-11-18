package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;

public class NetworkLuaBinding {
    public static void registerBindings(ILuaEngine engine, IRocketNetworkService networkService) {
        ILuaTable network = engine.createTable();
        network.rawset("GetOrCreateRemoteEvent", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.nilValue();
            String name = args[off].asString();
            return networkService.GetOrCreateRemoteEvent(name);
        });
        network.rawset("GetOrCreateRemoteFunction", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.nilValue();
            String name = args[off].asString();
            return networkService.GetOrCreateRemoteFunction(name);
        });
        network.rawset("InvokeServer", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) return engine.nilValue();
            String name = args[off].asString();
            ILuaTable argTable = args[off + 1].isTable() ? args[off + 1].asTable() : null;
            return networkService.InvokeServer(name, argTable != null ? unpackTable(argTable) : new com.luatweaker.api.vm.ILuaValue[0]);
        });
        network.rawset("InvokeClient", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 3) return engine.nilValue();
            String name = args[off].asString();
            String uuid = args[off + 1].asString();
            ILuaTable argTable = args[off + 2].isTable() ? args[off + 2].asTable() : null;
            return networkService.InvokeClient(name, uuid, argTable != null ? unpackTable(argTable) : new com.luatweaker.api.vm.ILuaValue[0]);
        });

        engine.registerService("NetworkService", network);
        engine.registerService("Network", network);
        engine.registerService("NetworkServiceImpl", networkService);
        engine.registerGlobal("NetworkService", network);
        engine.registerGlobal("Network", network);
    }

    private static com.luatweaker.api.vm.ILuaValue[] unpackTable(ILuaTable tbl) {
        java.util.ArrayList<com.luatweaker.api.vm.ILuaValue> list = new java.util.ArrayList<>();
        for (int i = 1; i <= tbl.length(); i++) {
            com.luatweaker.api.vm.ILuaValue val = tbl.rawget(i);
            if (val == null || val.isNil()) break;
            list.add(val);
        }
        return list.toArray(new com.luatweaker.api.vm.ILuaValue[0]);
    }
}
