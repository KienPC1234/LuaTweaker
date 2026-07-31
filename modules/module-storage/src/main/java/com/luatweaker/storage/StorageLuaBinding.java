package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

public class StorageLuaBinding {
    
    private static ILuaValue wrapDataStore(ILuaEngine engine, IRobloxStorageService.IDataStore store) {
        ILuaTable table = engine.createTable();
        table.rawset("GetAsync", args -> {
            if (args.length < 2) return engine.nilValue();
            String key = args[1].asString();
            Object res = store.GetAsync(key);
            if (res instanceof ILuaValue lv) return lv;
            if (res instanceof String s) return engine.wrapString(s);
            if (res instanceof Number n) return engine.wrapNumber(n.doubleValue());
            if (res instanceof Boolean b) return engine.wrapBoolean(b);
            if (res == null) return engine.nilValue();
            return engine.wrapUserdata(res);
        });
        table.rawset("SetAsync", args -> {
            if (args.length < 3) return null;
            String key = args[1].asString();
            ILuaValue val = args[2];
            store.SetAsync(key, val);
            return null;
        });
        return table;
    }

    public static void registerBindings(ILuaEngine engine, IRobloxStorageService storageService) {
        ILuaTable worldStorage = (ILuaTable) wrapDataStore(engine, storageService.GetWorldStorage());
        ILuaTable sessionStorage = (ILuaTable) wrapDataStore(engine, storageService.GetSessionStorage());

        ILuaTable playerStorageLookup = engine.createTable();
        playerStorageLookup.rawset("GetPlayerStorage", args -> {
            if (args.length < 2) return engine.nilValue();
            String playerUuid = args[1].asString();
            return wrapDataStore(engine, storageService.GetPlayerStorage(playerUuid));
        });

        engine.registerService("WorldStorage", worldStorage);
        engine.registerService("PlayerStorage", playerStorageLookup);
        engine.registerService("SessionStorage", sessionStorage);

        engine.registerGlobal("WorldStorage", worldStorage);
        engine.registerGlobal("PlayerStorage", playerStorageLookup);
        engine.registerGlobal("SessionStorage", sessionStorage);
    }
}
